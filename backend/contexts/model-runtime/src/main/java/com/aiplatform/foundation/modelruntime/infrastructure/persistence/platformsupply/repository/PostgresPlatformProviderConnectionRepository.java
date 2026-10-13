package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderConnectionEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderConnectionRequestEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用独立平台表原子保存连接、预设来源及幂等事实，普通分页由 MyBatis-Plus 处理。 */
public final class PostgresPlatformProviderConnectionRepository implements PlatformProviderConnectionRepository {
    private static final long CREATION_LOCK = 6_613_000_066L;
    private final PlatformProviderConnectionMapper mapper;
    private final PlatformProviderConnectionRequestMapper requests;
    private final TransactionTemplate transactions;
    private final TransactionTemplate reads;

    public PostgresPlatformProviderConnectionRepository(PlatformProviderConnectionMapper mapper,
            PlatformProviderConnectionRequestMapper requests, PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "平台连接映射器不能为空");
        this.requests = Objects.requireNonNull(requests, "幂等请求映射器不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
        this.reads = new TransactionTemplate(transactionManager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public PlatformProviderConnectionCreationOutcome create(PlatformProviderConnectionCreation creation) {
        Objects.requireNonNull(creation, "平台供应商连接创建请求不能为空");
        return Objects.requireNonNull(transactions.execute(status -> createInTransaction(creation)), "创建事务没有返回结果");
    }

    private PlatformProviderConnectionCreationOutcome createInTransaction(PlatformProviderConnectionCreation creation) {
        mapper.lockCreation(CREATION_LOCK);
        var keyHash = sha256(creation.idempotencyKey());
        var existing = requests.selectById(keyHash);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new ProviderConnectionIdempotencyConflictException();
            }
            return new PlatformProviderConnectionCreationOutcome(
                    findById(new PlatformProviderConnectionId(existing.getPlatformProviderConnectionId()))
                            .orElseThrow(() -> new IllegalStateException("幂等记录引用的平台连接不存在")), true);
        }
        var connection = creation.connection();
        try {
            mapper.insert(toEntity(connection));
            var request = new PlatformProviderConnectionRequestEntity();
            request.setIdempotencyKeyHash(keyHash);
            request.setRequestFingerprint(creation.requestFingerprint());
            request.setPlatformProviderConnectionId(connection.id().value());
            requests.insert(request);
        } catch (DuplicateKeyException exception) {
            throw new ProviderConnectionNameConflictException();
        }
        return new PlatformProviderConnectionCreationOutcome(connection, false);
    }

    @Override
    public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) {
        Objects.requireNonNull(id, "平台供应商连接标识不能为空");
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PlatformProviderConnectionEntity>lambdaQuery()
                .eq(PlatformProviderConnectionEntity::getId, id.value())
                .eq(PlatformProviderConnectionEntity::getScope, "PLATFORM")))
                .map(PostgresPlatformProviderConnectionRepository::toDomain);
    }

    @Override
    public PlatformProviderConnectionPage findPage(long pageNumber, long pageSize, String q) {
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L || (pageNumber - 1) > 100_000 / pageSize) throw new IllegalArgumentException("分页参数不正确");
        // 数据和总数在同一只读快照中计算，发布变化不能制造跨页假总数。
        return Objects.requireNonNull(reads.execute(status -> {
            var pagination = new Page<PlatformProviderConnectionEntity>(pageNumber, pageSize);
            pagination.setCountId("countDirectory");
            var page = mapper.selectDirectoryPage(pagination, "%" + Objects.requireNonNull(q, "搜索词不能为空")
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            return new PlatformProviderConnectionPage(page.getRecords().stream()
                    .map(PostgresPlatformProviderConnectionRepository::toDomain).toList(), page.getTotal());
        }), "平台目录只读查询没有返回结果");
    }

    @Override
    public long countPublished() { return mapper.countPublished(); }

    @Override
    public boolean update(PlatformProviderConnection connection, long expectedVersion) {
        Objects.requireNonNull(connection, "平台供应商连接不能为空");
        if (connection.version() != expectedVersion + 1L) throw new IllegalArgumentException("平台供应商连接新旧版本关系不正确");
        // 探测和凭据轮换只改变当前修订事实，不能丢失或替换冻结的预设来源。
        return mapper.update(Wrappers.<PlatformProviderConnectionEntity>lambdaUpdate()
                .eq(PlatformProviderConnectionEntity::getId, connection.id().value())
                .eq(PlatformProviderConnectionEntity::getScope, "PLATFORM")
                .eq(PlatformProviderConnectionEntity::getVersion, expectedVersion)
                .set(PlatformProviderConnectionEntity::getStatus, connection.status().name())
                .set(PlatformProviderConnectionEntity::getVersion, connection.version())
                .set(PlatformProviderConnectionEntity::getLastProbeSucceeded, connection.lastProbeSucceeded())
                .set(PlatformProviderConnectionEntity::getLastProbeCode, connection.lastProbeCode())
                .set(PlatformProviderConnectionEntity::getLastProbedAt, connection.lastProbedAt())) == 1;
    }

    private static PlatformProviderConnectionEntity toEntity(PlatformProviderConnection connection) {
        var row = new PlatformProviderConnectionEntity();
        row.setId(connection.id().value());
        row.setScope(connection.scope().name());
        row.setProviderKind(connection.providerKind().name());
        row.setDiscoveryProfile(connection.discoveryProfile().name());
        if (connection.preset() != null) {
            row.setProviderPresetId(connection.preset().providerId());
            row.setProviderPresetVersion(connection.preset().version());
            row.setCapabilityIntent(connection.preset().capability());
            row.setWorkspaceId(connection.preset().workspaceId());
        }
        row.setDisplayName(connection.displayName());
        row.setEndpoint(connection.endpoint().toString());
        row.setRegion(connection.region());
        row.setAuthenticationType(connection.authenticationType().name());
        row.setCredentialReference(connection.credentialReference().value());
        row.setStatus(connection.status().name());
        row.setVersion(connection.version());
        row.setLastProbeSucceeded(connection.lastProbeSucceeded());
        row.setLastProbeCode(connection.lastProbeCode());
        row.setLastProbedAt(connection.lastProbedAt());
        row.setCreatedAt(connection.createdAt());
        return row;
    }

    private static PlatformProviderConnection toDomain(PlatformProviderConnectionEntity row) {
        var preset = row.getProviderPresetId() == null ? null : new PlatformProviderPresetSelection(
                row.getProviderPresetId(), row.getProviderPresetVersion(), row.getCapabilityIntent(), row.getWorkspaceId());
        return PlatformProviderConnection.reconstitute(new PlatformProviderConnectionId(row.getId()),
                ProviderKind.valueOf(row.getProviderKind()), PlatformModelDiscoveryProfile.valueOf(row.getDiscoveryProfile()),
                preset, row.getDisplayName(), URI.create(row.getEndpoint()), row.getRegion(),
                AuthenticationType.valueOf(row.getAuthenticationType()), new CredentialReference(row.getCredentialReference()),
                ProviderConnectionStatus.valueOf(row.getStatus()), row.getVersion(), row.getLastProbeSucceeded(),
                row.getLastProbeCode(), row.getLastProbedAt(), row.getCreatedAt());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
