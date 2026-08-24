package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用独立平台表保存平台供应商连接及其幂等事实。 */
public final class PostgresPlatformProviderConnectionRepository
        implements PlatformProviderConnectionRepository {

    private static final long CREATION_LOCK = 6_613_000_066L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresPlatformProviderConnectionRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "数据库访问器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public PlatformProviderConnectionCreationOutcome create(PlatformProviderConnectionCreation creation) {
        Objects.requireNonNull(creation, "平台供应商连接创建请求不能为空");
        var outcome = transactions.execute(status -> createInTransaction(creation));
        return Objects.requireNonNull(outcome, "平台供应商连接创建事务没有返回结果");
    }

    private PlatformProviderConnectionCreationOutcome createInTransaction(
            PlatformProviderConnectionCreation creation) {
        jdbc.execute((java.sql.Connection connection) -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, CREATION_LOCK);
                statement.execute();
            }
            return null;
        });
        var keyHash = sha256(creation.idempotencyKey());
        var existing = jdbc.query("""
                SELECT request_fingerprint, platform_provider_connection_id
                FROM model_runtime.platform_provider_connection_request
                WHERE idempotency_key_hash = ?
                """, rs -> rs.next()
                        ? new ExistingRequest(rs.getString(1), rs.getLong(2))
                        : null, keyHash);
        if (existing != null) {
            if (!existing.fingerprint().equals(creation.requestFingerprint())) {
                throw new ProviderConnectionIdempotencyConflictException();
            }
            return new PlatformProviderConnectionCreationOutcome(
                    findById(new PlatformProviderConnectionId(existing.connectionId()))
                            .orElseThrow(() -> new IllegalStateException("幂等记录引用的平台连接不存在")),
                    true);
        }
        var connection = creation.connection();
        try {
            jdbc.update("""
                    INSERT INTO model_runtime.platform_provider_connection (
                        id, scope, provider_kind, discovery_profile, display_name, endpoint, region, authentication_type,
                        credential_reference, status, version, last_probe_succeeded, last_probe_code,
                        last_probed_at, created_at)
                    VALUES (?, 'PLATFORM', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    connection.id().value(), connection.providerKind().name(), connection.discoveryProfile().name(),
                    connection.displayName(),
                    connection.endpoint().toString(), connection.region(), connection.authenticationType().name(),
                    connection.credentialReference().value(), connection.status().name(), connection.version(),
                    connection.lastProbeSucceeded(), connection.lastProbeCode(), timestamp(connection.lastProbedAt()),
                    timestamp(connection.createdAt()));
            jdbc.update("""
                    INSERT INTO model_runtime.platform_provider_connection_request (
                        idempotency_key_hash, request_fingerprint, platform_provider_connection_id)
                    VALUES (?, ?, ?)
                    """, keyHash, creation.requestFingerprint(), connection.id().value());
        } catch (DuplicateKeyException exception) {
            throw new ProviderConnectionNameConflictException();
        }
        return new PlatformProviderConnectionCreationOutcome(connection, false);
    }

    @Override
    public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) {
        Objects.requireNonNull(id, "平台供应商连接标识不能为空");
        return Optional.ofNullable(jdbc.query("""
                SELECT id, provider_kind, discovery_profile, display_name, endpoint, region, authentication_type,
                    credential_reference, status, version, last_probe_succeeded, last_probe_code,
                    last_probed_at, created_at
                FROM model_runtime.platform_provider_connection
                WHERE id = ? AND scope = 'PLATFORM'
                """, rs -> rs.next() ? toDomain(rs) : null, id.value())
                );
    }

    @Override
    public PlatformProviderConnectionPage findPage(long pageNumber, long pageSize) {
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        var items = jdbc.query("""
                SELECT id, provider_kind, discovery_profile, display_name, endpoint, region, authentication_type,
                    credential_reference, status, version, last_probe_succeeded, last_probe_code,
                    last_probed_at, created_at
                FROM model_runtime.platform_provider_connection
                WHERE scope = 'PLATFORM'
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> toDomain(rs), pageSize, (pageNumber - 1L) * pageSize);
        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.platform_provider_connection WHERE scope = 'PLATFORM'",
                Long.class);
        return new PlatformProviderConnectionPage(items, Objects.requireNonNull(total));
    }

    @Override
    public boolean update(PlatformProviderConnection connection, long expectedVersion) {
        Objects.requireNonNull(connection, "平台供应商连接不能为空");
        if (connection.version() != expectedVersion + 1L) {
            throw new IllegalArgumentException("平台供应商连接新旧版本关系不正确");
        }
        return jdbc.update("""
                UPDATE model_runtime.platform_provider_connection
                SET status = ?, version = ?, last_probe_succeeded = ?, last_probe_code = ?, last_probed_at = ?
                WHERE id = ? AND scope = 'PLATFORM' AND version = ?
                """, connection.status().name(), connection.version(), connection.lastProbeSucceeded(),
                connection.lastProbeCode(), timestamp(connection.lastProbedAt()), connection.id().value(), expectedVersion) == 1;
    }

    private static PlatformProviderConnection toDomain(ResultSet row) throws java.sql.SQLException {
        return PlatformProviderConnection.reconstitute(
                new PlatformProviderConnectionId(row.getLong("id")),
                ProviderKind.valueOf(row.getString("provider_kind")),
                PlatformModelDiscoveryProfile.valueOf(row.getString("discovery_profile")),
                row.getString("display_name"),
                URI.create(row.getString("endpoint")), row.getString("region"),
                AuthenticationType.valueOf(row.getString("authentication_type")),
                new CredentialReference(row.getString("credential_reference")),
                ProviderConnectionStatus.valueOf(row.getString("status")), row.getLong("version"),
                (Boolean) row.getObject("last_probe_succeeded"), row.getString("last_probe_code"),
                row.getTimestamp("last_probed_at") == null ? null : row.getTimestamp("last_probed_at").toInstant(),
                row.getTimestamp("created_at").toInstant());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private record ExistingRequest(String fingerprint, long connectionId) {}
}
