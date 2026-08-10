package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentQuotaExceededException;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.entity.ModelDeploymentEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.entity.ModelDeploymentRequestEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.projection.ModelDeploymentRow;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现租户内幂等和配额的模型部署仓储。
 */
public final class PostgresModelDeploymentRepository
        implements ModelDeploymentRepository {

    private final ModelDeploymentMapper mapper;
    private final ModelDeploymentRequestMapper requestMapper;
    private final TransactionTemplate transactions;

    public PostgresModelDeploymentRepository(
            ModelDeploymentMapper mapper,
            ModelDeploymentRequestMapper requestMapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "模型部署映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "模型部署幂等请求映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public ModelDeploymentCreationOutcome create(
            ModelDeploymentCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "模型部署创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户模型部署配额必须大于零");
        }
        var result = transactions.execute(
                status -> createInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "模型部署创建事务没有返回结果");
    }

    @Override
    public Optional<ModelDeployment> findById(
            TenantId tenantId,
            ModelDeploymentId deploymentId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(deploymentId, "模型部署标识不能为空");
        return Optional.ofNullable(mapper.findById(tenantId.value(), deploymentId.value()))
                .map(PostgresModelDeploymentRepository::toDomain);
    }

    @Override
    public ModelDeploymentPage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        var page = mapper.findPage(
                new Page<>(pageNumber, pageSize),
                tenantId.value());
        var items = page.getRecords().stream()
                .map(PostgresModelDeploymentRepository::toDomain)
                .toList();
        return new ModelDeploymentPage(items, page.getTotal());
    }

    @Override
    public boolean update(ModelDeployment deployment, long expectedVersion) {
        Objects.requireNonNull(deployment, "模型部署不能为空");
        if (deployment.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("模型部署新旧版本关系不正确");
        }
        return mapper.update(
                null,
                Wrappers.<ModelDeploymentEntity>lambdaUpdate()
                        .eq(ModelDeploymentEntity::getTenantId, deployment.tenantId().value())
                        .eq(ModelDeploymentEntity::getId, deployment.id().value())
                        .eq(ModelDeploymentEntity::getVersion, expectedVersion)
                        .set(ModelDeploymentEntity::getStatus, deployment.status().name())
                        .set(ModelDeploymentEntity::getVersion, deployment.version()))
                == 1;
    }

    private ModelDeploymentCreationOutcome createInTransaction(
            ModelDeploymentCreation creation,
            int tenantLimit) {
        var deployment = creation.deployment();
        var tenantId = deployment.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = requestMapper.selectOne(
                Wrappers.<ModelDeploymentRequestEntity>lambdaQuery()
                        .eq(ModelDeploymentRequestEntity::getTenantId, tenantId)
                        .eq(ModelDeploymentRequestEntity::getIdempotencyKeyHash, keyHash));
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) {
                throw new ModelDeploymentIdempotencyConflictException();
            }
            var replayed = mapper.findById(
                    tenantId,
                    existing.getModelDeploymentId());
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的模型部署不存在");
            }
            return new ModelDeploymentCreationOutcome(toDomain(replayed), true);
        }
        if (mapper.selectCount(
                Wrappers.<ModelDeploymentEntity>lambdaQuery()
                        .eq(ModelDeploymentEntity::getTenantId, tenantId)) >= tenantLimit) {
            throw new ModelDeploymentQuotaExceededException();
        }
        try {
            mapper.insertDeployment(
                    tenantId,
                    deployment.id().value(),
                    deployment.providerConnectionId().value(),
                    deployment.providerConnectionRevision(),
                    deployment.modelCode(),
                    deployment.displayName(),
                    deployment.modelType().name(),
                    capabilities(deployment.capabilities()),
                    deployment.contextWindow(),
                    deployment.outputLimit(),
                    deployment.embeddingDimension(),
                    deployment.status().name(),
                    deployment.version(),
                    deployment.createdAt());
            requestMapper.insert(toRequestEntity(creation, keyHash));
        } catch (DuplicateKeyException exception) {
            throw new ModelDeploymentNameConflictException();
        }
        return new ModelDeploymentCreationOutcome(deployment, false);
    }

    private static ModelDeployment toDomain(ModelDeploymentRow row) {
        return ModelDeployment.reconstitute(
                TenantId.parse(row.getTenantId()),
                new ModelDeploymentId(row.getId()),
                new ProviderConnectionId(row.getProviderConnectionId()),
                row.getProviderConnectionRevision(),
                row.getModelCode(),
                row.getDisplayName(),
                ModelType.valueOf(row.getModelType()),
                parseCapabilities(row.getCapabilities()),
                row.getContextWindow(),
                row.getOutputLimit(),
                row.getEmbeddingDimension(),
                row.getLastVerificationId() == null
                        ? null
                        : new ModelCapabilityVerificationId(row.getLastVerificationId()),
                row.getVerificationExpiresAt(),
                ModelDeploymentStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt());
    }

    private static String capabilities(Set<ModelCapability> values) {
        return values.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private static Set<ModelCapability> parseCapabilities(String value) {
        return Arrays.stream(value.split(",", -1))
                .map(ModelCapability::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ModelDeploymentRequestEntity toRequestEntity(
            ModelDeploymentCreation creation,
            String keyHash) {
        var entity = new ModelDeploymentRequestEntity();
        entity.setId(creation.deployment().id().value());
        entity.setTenantId(creation.deployment().tenantId().value());
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(creation.requestFingerprint());
        entity.setModelDeploymentId(creation.deployment().id().value());
        entity.setCreatedAt(creation.deployment().createdAt());
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
