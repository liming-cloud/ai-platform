package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用平台专表和事务锁保存部署及全局幂等事实。 */
public final class PostgresPlatformModelDeploymentRepository implements PlatformModelDeploymentRepository {
    private static final long CREATION_LOCK = 6_613_000_067L;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresPlatformModelDeploymentRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "数据库访问器不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public PlatformModelDeploymentCreationOutcome create(PlatformModelDeploymentCreation creation) {
        creation = Objects.requireNonNull(creation, "平台模型部署创建请求不能为空");
        var captured = creation;
        var outcome = transactions.execute(status -> createInTransaction(captured));
        return Objects.requireNonNull(outcome, "平台模型部署创建事务没有返回结果");
    }

    @Override
    public Optional<PlatformModelDeployment> findById(PlatformModelDeploymentId id) {
        id = Objects.requireNonNull(id, "平台模型部署标识不能为空");
        return Optional.ofNullable(jdbc.query("""
                SELECT id, platform_provider_connection_id, platform_provider_connection_revision,
                    model_code, display_name, model_type, array_to_string(capabilities, ',') AS capabilities,
                    context_window, output_limit, embedding_dimension, last_verification_id,
                    verification_expires_at, status, version, created_at
                FROM model_runtime.platform_model_deployment WHERE id = ? AND scope = 'PLATFORM'
                """, rs -> rs.next() ? toDomain(rs) : null, id.value()));
    }

    @Override
    public PlatformModelDeploymentPage findPage(long pageNumber, long pageSize) {
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L) throw new IllegalArgumentException("分页参数不正确");
        var items = jdbc.query("""
                SELECT id, platform_provider_connection_id, platform_provider_connection_revision,
                    model_code, display_name, model_type, array_to_string(capabilities, ',') AS capabilities,
                    context_window, output_limit, embedding_dimension, last_verification_id,
                    verification_expires_at, status, version, created_at
                FROM model_runtime.platform_model_deployment WHERE scope = 'PLATFORM'
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> toDomain(rs), pageSize, (pageNumber - 1L) * pageSize);
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_model_deployment WHERE scope = 'PLATFORM'", Long.class);
        return new PlatformModelDeploymentPage(items, Objects.requireNonNull(total));
    }

    @Override
    public boolean update(PlatformModelDeployment deployment, long expectedVersion) {
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
        if (deployment.version() != expectedVersion + 1L) throw new IllegalArgumentException("平台模型部署新旧版本关系不正确");
        return jdbc.update("""
                UPDATE model_runtime.platform_model_deployment
                SET status = ?, last_verification_id = ?, verification_expires_at = ?, version = ?
                WHERE id = ? AND scope = 'PLATFORM' AND version = ?
                """, deployment.status().name(), deployment.lastVerificationId() == null ? null : deployment.lastVerificationId().value(),
                timestamp(deployment.verificationExpiresAt()), deployment.version(), deployment.id().value(), expectedVersion) == 1;
    }

    private PlatformModelDeploymentCreationOutcome createInTransaction(PlatformModelDeploymentCreation creation) {
        jdbc.execute((java.sql.Connection connection) -> { try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) { statement.setLong(1, CREATION_LOCK); statement.execute(); } return null; });
        var keyHash = sha256(creation.idempotencyKey());
        var existing = jdbc.query("SELECT request_fingerprint, platform_model_deployment_id FROM model_runtime.platform_model_deployment_request WHERE idempotency_key_hash = ?",
                rs -> rs.next() ? new ExistingRequest(rs.getString(1), rs.getLong(2)) : null, keyHash);
        if (existing != null) {
            if (!existing.fingerprint().equals(creation.requestFingerprint())) throw new ModelDeploymentIdempotencyConflictException();
            return new PlatformModelDeploymentCreationOutcome(findById(new PlatformModelDeploymentId(existing.deploymentId()))
                    .orElseThrow(() -> new IllegalStateException("幂等记录引用的平台模型部署不存在")), true);
        }
        var deployment = creation.deployment();
        try {
            jdbc.update("""
                    INSERT INTO model_runtime.platform_model_deployment (
                        id, scope, platform_provider_connection_id, platform_provider_connection_revision,
                        model_code, display_name, model_type, capabilities, context_window, output_limit,
                        embedding_dimension, last_verification_id, verification_expires_at, status, version, created_at)
                    VALUES (?, 'PLATFORM', ?, ?, ?, ?, ?, string_to_array(?, ','), ?, ?, ?, ?, ?, ?, ?, ?)
                    """, deployment.id().value(), deployment.providerConnectionId().value(), deployment.providerConnectionRevision(),
                    deployment.modelCode(), deployment.displayName(), deployment.modelType().name(), capabilities(deployment.capabilities()),
                    deployment.contextWindow(), deployment.outputLimit(), deployment.embeddingDimension(), null, null,
                    deployment.status().name(), deployment.version(), timestamp(deployment.createdAt()));
            jdbc.update("INSERT INTO model_runtime.platform_model_deployment_request (idempotency_key_hash, request_fingerprint, platform_model_deployment_id) VALUES (?, ?, ?)",
                    keyHash, creation.requestFingerprint(), deployment.id().value());
        } catch (DuplicateKeyException exception) { throw new ModelDeploymentNameConflictException(); }
        return new PlatformModelDeploymentCreationOutcome(deployment, false);
    }

    static PlatformModelDeployment toDomain(ResultSet row) throws java.sql.SQLException {
        var verification = row.getObject("last_verification_id") == null ? null : new PlatformModelCapabilityVerificationId(row.getLong("last_verification_id"));
        var expires = row.getTimestamp("verification_expires_at");
        return PlatformModelDeployment.reconstitute(new PlatformModelDeploymentId(row.getLong("id")),
                new PlatformProviderConnectionId(row.getLong("platform_provider_connection_id")), row.getLong("platform_provider_connection_revision"),
                row.getString("model_code"), row.getString("display_name"), ModelType.valueOf(row.getString("model_type")),
                Arrays.stream(row.getString("capabilities").split(",", -1)).map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet()),
                row.getInt("context_window"), (Integer) row.getObject("output_limit"), (Integer) row.getObject("embedding_dimension"), verification,
                expires == null ? null : expires.toInstant(), PlatformModelDeploymentStatus.valueOf(row.getString("status")), row.getLong("version"), row.getTimestamp("created_at").toInstant());
    }
    private static String capabilities(java.util.Set<ModelCapability> values) { return values.stream().sorted(Comparator.comparing(Enum::name)).map(Enum::name).collect(Collectors.joining(",")); }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception); } }
    private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }
    private record ExistingRequest(String fingerprint, long deploymentId) {}
}
