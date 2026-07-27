package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionQuotaExceededException;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.entity.ProviderConnectionEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.entity.ProviderConnectionRequestEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionRequestMapper;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
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

/**
 * 使用 PostgreSQL 事务锁实现租户内幂等和配额的供应商连接仓储。
 */
public final class PostgresProviderConnectionRepository
        implements ProviderConnectionRepository {

    private final ProviderConnectionMapper mapper;
    private final ProviderConnectionRequestMapper requestMapper;
    private final TransactionTemplate transactions;

    public PostgresProviderConnectionRepository(
            ProviderConnectionMapper mapper,
            ProviderConnectionRequestMapper requestMapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "供应商连接映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "供应商连接幂等请求映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public ProviderConnectionCreationOutcome create(
            ProviderConnectionCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "供应商连接创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户供应商连接配额必须大于零");
        }
        var result = transactions.execute(
                status -> createInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "供应商连接创建事务没有返回结果");
    }

    @Override
    public Optional<ProviderConnection> findById(
            TenantId tenantId,
            ProviderConnectionId connectionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(connectionId, "供应商连接标识不能为空");
        return Optional.ofNullable(mapper.selectOne(
                        Wrappers.<ProviderConnectionEntity>lambdaQuery()
                                .eq(ProviderConnectionEntity::getTenantId, tenantId.value())
                                .eq(ProviderConnectionEntity::getId, connectionId.value())))
                .map(PostgresProviderConnectionRepository::toDomain);
    }

    @Override
    public ProviderConnectionPage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        var page = mapper.selectPage(
                new Page<ProviderConnectionEntity>(pageNumber, pageSize),
                Wrappers.<ProviderConnectionEntity>lambdaQuery()
                        .eq(ProviderConnectionEntity::getTenantId, tenantId.value())
                        .orderByDesc(
                                ProviderConnectionEntity::getCreatedAt,
                                ProviderConnectionEntity::getId));
        return new ProviderConnectionPage(
                page.getRecords().stream()
                        .map(PostgresProviderConnectionRepository::toDomain)
                        .toList(),
                page.getTotal());
    }

    @Override
    public boolean update(ProviderConnection connection, long expectedVersion) {
        Objects.requireNonNull(connection, "供应商连接不能为空");
        if (connection.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("供应商连接新旧版本关系不正确");
        }
        return mapper.update(
                null,
                Wrappers.<ProviderConnectionEntity>lambdaUpdate()
                        .eq(ProviderConnectionEntity::getTenantId, connection.tenantId().value())
                        .eq(ProviderConnectionEntity::getId, connection.id().value())
                        .eq(ProviderConnectionEntity::getVersion, expectedVersion)
                        .set(ProviderConnectionEntity::getStatus, connection.status().name())
                        .set(ProviderConnectionEntity::getVersion, connection.version())
                        .set(
                                ProviderConnectionEntity::getLastProbeSucceeded,
                                connection.lastProbeSucceeded())
                        .set(
                                ProviderConnectionEntity::getLastProbeCode,
                                connection.lastProbeCode())
                        .set(
                                ProviderConnectionEntity::getLastProbedAt,
                                connection.lastProbedAt()))
                == 1;
    }

    private ProviderConnectionCreationOutcome createInTransaction(
            ProviderConnectionCreation creation,
            int tenantLimit) {
        var connection = creation.connection();
        var tenantId = connection.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = requestMapper.selectOne(
                Wrappers.<ProviderConnectionRequestEntity>lambdaQuery()
                        .eq(ProviderConnectionRequestEntity::getTenantId, tenantId)
                        .eq(ProviderConnectionRequestEntity::getIdempotencyKeyHash, keyHash));
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new ProviderConnectionIdempotencyConflictException();
            }
            var replayed = mapper.selectOne(
                    Wrappers.<ProviderConnectionEntity>lambdaQuery()
                            .eq(ProviderConnectionEntity::getTenantId, tenantId)
                            .eq(
                                    ProviderConnectionEntity::getId,
                                    existing.getProviderConnectionId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的供应商连接不存在");
            }
            return new ProviderConnectionCreationOutcome(toDomain(replayed), true);
        }
        if (mapper.selectCount(
                Wrappers.<ProviderConnectionEntity>lambdaQuery()
                        .eq(ProviderConnectionEntity::getTenantId, tenantId)
                        .ne(
                                ProviderConnectionEntity::getStatus,
                                ProviderConnectionStatus.DISABLED.name())) >= tenantLimit) {
            throw new ProviderConnectionQuotaExceededException();
        }
        try {
            mapper.insert(toEntity(connection));
            requestMapper.insert(toRequestEntity(creation, keyHash));
        } catch (DuplicateKeyException exception) {
            throw new ProviderConnectionNameConflictException();
        }
        return new ProviderConnectionCreationOutcome(connection, false);
    }

    private static ProviderConnection toDomain(ProviderConnectionEntity entity) {
        return ProviderConnection.reconstitute(
                new TenantId(entity.getTenantId()),
                new ProviderConnectionId(entity.getId()),
                ProviderKind.valueOf(entity.getProviderKind()),
                entity.getDisplayName(),
                URI.create(entity.getEndpoint()),
                entity.getRegion(),
                AuthenticationType.valueOf(entity.getAuthenticationType()),
                new CredentialReference(entity.getCredentialReference()),
                ProviderConnectionStatus.valueOf(entity.getStatus()),
                entity.getVersion(),
                entity.getLastProbeSucceeded(),
                entity.getLastProbeCode(),
                entity.getLastProbedAt(),
                entity.getCreatedAt());
    }

    private static ProviderConnectionEntity toEntity(ProviderConnection connection) {
        var entity = new ProviderConnectionEntity();
        entity.setId(connection.id().value());
        entity.setTenantId(connection.tenantId().value());
        entity.setProviderKind(connection.providerKind().name());
        entity.setDisplayName(connection.displayName());
        entity.setEndpoint(connection.endpoint().toString());
        entity.setRegion(connection.region());
        entity.setAuthenticationType(connection.authenticationType().name());
        entity.setCredentialReference(connection.credentialReference().value());
        entity.setStatus(connection.status().name());
        entity.setVersion(connection.version());
        entity.setLastProbeSucceeded(connection.lastProbeSucceeded());
        entity.setLastProbeCode(connection.lastProbeCode());
        entity.setLastProbedAt(connection.lastProbedAt());
        entity.setCreatedAt(connection.createdAt());
        return entity;
    }

    private static ProviderConnectionRequestEntity toRequestEntity(
            ProviderConnectionCreation creation,
            String keyHash) {
        var entity = new ProviderConnectionRequestEntity();
        entity.setId(creation.connection().id().value());
        entity.setTenantId(creation.connection().tenantId().value());
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(creation.requestFingerprint());
        entity.setProviderConnectionId(creation.connection().id().value());
        entity.setCreatedAt(creation.connection().createdAt());
        return entity;
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

}
