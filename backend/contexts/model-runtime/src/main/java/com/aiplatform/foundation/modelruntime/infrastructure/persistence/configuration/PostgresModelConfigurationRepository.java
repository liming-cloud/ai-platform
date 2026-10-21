package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.application.configuration.exception.ControlledModelCatalogConflictException;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ControlledModelCatalogPage;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationPage;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ControlledModelCatalogEntryEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationEntity;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;

/**
 * PostgreSQL 受控目录与模型配置仓储，所有配置所有权由领域对象验证。
 */
public final class PostgresModelConfigurationRepository implements ModelConfigurationRepository {

    private final ControlledModelCatalogEntryMapper catalogMapper;
    private final ModelConfigurationMapper configurationMapper;
    private final java.util.function.Supplier<com.aiplatform.foundation.security.identity.TenantActivityFacts> tenants;
    private final java.util.function.Supplier<com.aiplatform.foundation.security.identity.TenantFeatureAvailability> features;
    private final org.springframework.transaction.support.TransactionTemplate reads;
    private final org.springframework.transaction.support.TransactionTemplate writes;

    public PostgresModelConfigurationRepository(
            ControlledModelCatalogEntryMapper catalogMapper,
            ModelConfigurationMapper configurationMapper,
            java.util.function.Supplier<com.aiplatform.foundation.security.identity.TenantActivityFacts> tenants,
            java.util.function.Supplier<com.aiplatform.foundation.security.identity.TenantFeatureAvailability> features,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        this.catalogMapper = Objects.requireNonNull(catalogMapper, "受控模型目录映射器不能为空");
        this.configurationMapper = Objects.requireNonNull(configurationMapper, "模型配置映射器不能为空");
        this.tenants = Objects.requireNonNull(tenants);
        this.features = Objects.requireNonNull(features);
        reads = new org.springframework.transaction.support.TransactionTemplate(Objects.requireNonNull(transactions));
        writes = new org.springframework.transaction.support.TransactionTemplate(transactions);
        writes.setTimeout(3);
        reads.setReadOnly(true);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
        Objects.requireNonNull(id, "受控模型目录条目标识不能为空");
        return Optional.ofNullable(catalogMapper.selectById(id.value()))
                .map(PostgresModelConfigurationRepository::toCatalogDomain);
    }

    @Override
    public ControlledModelCatalogPage findPublishedCatalogPage(
            TenantId tenantId, long pageNumber, long pageSize) {
        if (pageNumber < 1 || pageSize < 1) {
            throw new IllegalArgumentException("受控模型目录分页参数必须为正整数");
        }
        Objects.requireNonNull(tenantId);
        return Objects.requireNonNull(reads.execute(status -> {
            if (!tenantActive(tenantId)) return new ControlledModelCatalogPage(java.util.List.of(), pageNumber, pageSize, 0);
            var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<ControlledModelCatalogEntryEntity>(pageNumber, pageSize);
            page.setOptimizeCountSql(false);
            var result = catalogMapper.selectPublishedAvailablePage(page, tenantId.value());
            return new ControlledModelCatalogPage(result.getRecords().stream()
                    .map(PostgresModelConfigurationRepository::toCatalogDomain).toList(), pageNumber, pageSize, result.getTotal());
        }));
    }

    @Override
    public boolean isSupplyAvailable(TenantId tenantId, ControlledModelCatalogEntryId id) {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(id);
        return Boolean.TRUE.equals(reads.execute(status -> tenantActive(tenantId)
                && catalogMapper.isSupplyAvailable(tenantId.value(), id.value())));
    }

    @Override
    public void lockSupply(TenantId tenantId, ControlledModelCatalogEntryId id) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("供应提交锁必须处于短写事务");
        if (catalogMapper.lockSupplySource(id.value()) == null || catalogMapper.lockSupplyDeployment(id.value()) == null)
            throw new IllegalStateException("供应来源或部署已失效");
        Objects.requireNonNull(tenants.get(), "活动租户事实未装配").requireActive(java.util.Set.of(tenantId.value()));
        Objects.requireNonNull(features.get(), "当前租户功能事实未装配").requireAvailable(tenantId, "modelGovernance");
        if (catalogMapper.lockSupplyGrant(tenantId.value(), id.value()) == null)
            throw new IllegalStateException("当前租户供应授权不存在或已失效");
    }

    @Override
    public void saveWithSupply(TenantId tenantId, ModelConfiguration configuration) {
        requireScope(tenantId, configuration);
        if (configuration.status() != ModelConfigurationStatus.DRAFT || configuration.version() != 0)
            throw new IllegalArgumentException("新配置必须保持初始草稿状态");
        writes.executeWithoutResult(status -> {
            lockSupply(tenantId, configuration.catalogEntryId());
            save(configuration);
        });
    }

    @Override
    public void activateWithSupply(TenantId tenantId, ModelConfiguration configuration) {
        requireScope(tenantId, configuration);
        if (configuration.status() != ModelConfigurationStatus.ACTIVE || configuration.version() < 1)
            throw new IllegalArgumentException("启用必须绑定精确活动修订");
        writes.executeWithoutResult(status -> {
            lockSupply(tenantId, configuration.catalogEntryId());
            updateConfiguration(configuration);
        });
    }

    private static void requireScope(TenantId tenantId, ModelConfiguration configuration) {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(configuration);
        if (configuration.tenantId() != null && !configuration.tenantId().equals(tenantId))
            throw new IllegalArgumentException("供应提交租户与配置归属不一致");
    }

    private boolean tenantActive(TenantId id) {
        var owner = tenants.get();
        if (owner == null) throw new IllegalStateException("当前租户活动事实未装配");
        if (!owner.isActive(id.value())) return false;
        Objects.requireNonNull(features.get(), "当前租户功能事实未装配").requireAvailable(id, "modelGovernance");
        return true;
    }

    @Override
    public Optional<ControlledModelCatalogEntry> findPublishedByDeployment(PlatformModelDeploymentId id, long revision) {
        return Optional.ofNullable(catalogMapper.selectPublishedByDeployment(id.value(), revision))
                .map(PostgresModelConfigurationRepository::toCatalogDomain);
    }

    @Override
    public void saveCatalogEntry(ControlledModelCatalogEntry entry) {
        Objects.requireNonNull(entry, "受控模型目录条目不能为空");
        try {
            catalogMapper.insert(toEntity(entry));
        } catch (DuplicateKeyException exception) {
            throw new ControlledModelCatalogConflictException(exception);
        }
    }

    @Override
    public void updateCatalogEntry(ControlledModelCatalogEntry entry) {
        Objects.requireNonNull(entry, "受控模型目录条目不能为空");
        if (catalogMapper.update(null, Wrappers.<ControlledModelCatalogEntryEntity>lambdaUpdate()
                .eq(ControlledModelCatalogEntryEntity::getId, entry.id().value())
                .eq(ControlledModelCatalogEntryEntity::getVersion, entry.version() - 1)
                .set(ControlledModelCatalogEntryEntity::getStatus, entry.status().name())
                .set(ControlledModelCatalogEntryEntity::getVersion, entry.version())) != 1) {
            throw new IllegalStateException("受控模型目录条目版本已经变化");
        }
    }

    @Override
    public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
        Objects.requireNonNull(id, "模型配置标识不能为空");
        return Optional.ofNullable(configurationMapper.selectById(id.value())).map(row ->
                ModelConfiguration.reconstitute(
                        new ModelConfigurationId(row.getId()),
                        ModelConfigurationScope.valueOf(row.getScope()),
                        row.getTenantId() == null ? null : new TenantId(row.getTenantId()),
                        row.getOwnerMembershipId() == null ? null
                                : new ModelConfigurationOwnerId(row.getOwnerMembershipId()),
                        row.getDisplayName(),
                        new ControlledModelCatalogEntryId(row.getCatalogEntryId()),
                        ModelConfigurationStatus.valueOf(row.getStatus()),
                        row.getVersion(),
                        row.getCreatedAt()));
    }

    @Override
    public ModelConfigurationPage findVisiblePage(
            TenantId tenantId,
            ModelConfigurationOwnerId membershipId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(membershipId, "成员标识不能为空");
        if (pageNumber < 1 || pageSize < 1) {
            throw new IllegalArgumentException("模型配置分页参数必须为正整数");
        }
        var offset = Math.multiplyExact(pageNumber - 1, pageSize);
        var items = configurationMapper.selectVisiblePage(
                        tenantId.value(), membershipId.value(), pageSize, offset)
                .stream()
                .map(PostgresModelConfigurationRepository::toDomain)
                .toList();
        return new ModelConfigurationPage(
                items,
                pageNumber,
                pageSize,
                configurationMapper.countVisible(tenantId.value(), membershipId.value()));
    }

    @Override
    public void updateConfiguration(ModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "模型配置不能为空");
        if (configurationMapper.update(null, Wrappers.<ModelConfigurationEntity>lambdaUpdate()
                .eq(ModelConfigurationEntity::getId, configuration.id().value())
                .eq(ModelConfigurationEntity::getVersion, configuration.version() - 1)
                .set(ModelConfigurationEntity::getStatus, configuration.status().name())
                .set(ModelConfigurationEntity::getVersion, configuration.version())) != 1) {
            throw new IllegalStateException("模型配置版本已经变化");
        }
    }

    @Override
    public void save(ModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "模型配置不能为空");
        configurationMapper.insert(toEntity(configuration));
    }

    private static ModelRuntimeCapabilities requireCapabilities(ControlledModelCatalogEntryEntity row) {
        if (row.getContextWindow() == null
                || (row.getOutputLimit() == null && row.getEmbeddingDimension() == null && row.getRerankCandidateLimit() == null)) {
            throw new IllegalStateException("受控模型目录条目缺少运行时能力快照，必须由平台重新发布");
        }
        return new ModelRuntimeCapabilities(
                row.getContextWindow(), row.getOutputLimit(), row.getEmbeddingDimension(), row.getRerankCandidateLimit());
    }

    private static ControlledModelCatalogEntry toCatalogDomain(
            ControlledModelCatalogEntryEntity row) {
        return ControlledModelCatalogEntry.reconstitute(
                new ControlledModelCatalogEntryId(row.getId()),
                ProviderKind.valueOf(row.getProviderKind()),
                URI.create(row.getEndpoint()),
                row.getModelCode(),
                ModelType.valueOf(row.getModelType()),
                requireCapabilities(row),
                ControlledModelCatalogEntryStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt(),
                row.getSourcePlatformDeploymentId() == null ? null
                        : new PlatformModelDeploymentId(row.getSourcePlatformDeploymentId()),
                row.getSourcePlatformDeploymentRevision());
    }

    private static ModelConfiguration toDomain(ModelConfigurationEntity row) {
        return ModelConfiguration.reconstitute(
                new ModelConfigurationId(row.getId()),
                ModelConfigurationScope.valueOf(row.getScope()),
                row.getTenantId() == null ? null : new TenantId(row.getTenantId()),
                row.getOwnerMembershipId() == null ? null
                        : new ModelConfigurationOwnerId(row.getOwnerMembershipId()),
                row.getDisplayName(),
                new ControlledModelCatalogEntryId(row.getCatalogEntryId()),
                ModelConfigurationStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt());
    }

    private static ControlledModelCatalogEntryEntity toEntity(ControlledModelCatalogEntry entry) {
        var entity = new ControlledModelCatalogEntryEntity();
        entity.setId(entry.id().value());
        entity.setProviderKind(entry.providerKind().name());
        entity.setEndpoint(entry.endpoint().toString());
        entity.setModelCode(entry.modelCode());
        entity.setModelType(entry.modelType().name());
        entity.setContextWindow(entry.capabilities().contextWindow());
        entity.setOutputLimit(entry.capabilities().outputLimit());
        entity.setEmbeddingDimension(entry.capabilities().embeddingDimension());
        entity.setRerankCandidateLimit(entry.capabilities().rerankCandidateLimit());
        entity.setSourcePlatformDeploymentId(entry.sourceDeploymentId() == null ? null : entry.sourceDeploymentId().value());
        entity.setSourcePlatformDeploymentRevision(entry.sourceDeploymentRevision());
        entity.setStatus(entry.status().name());
        entity.setVersion(entry.version());
        entity.setCreatedAt(entry.createdAt());
        return entity;
    }

    private static ModelConfigurationEntity toEntity(ModelConfiguration configuration) {
        var entity = new ModelConfigurationEntity();
        entity.setId(configuration.id().value());
        entity.setScope(configuration.scope().name());
        entity.setTenantId(configuration.tenantId() == null ? null : configuration.tenantId().value());
        entity.setOwnerMembershipId(configuration.ownerMembershipId() == null
                ? null : configuration.ownerMembershipId().value());
        entity.setDisplayName(configuration.displayName());
        entity.setCatalogEntryId(configuration.catalogEntryId().value());
        entity.setStatus(configuration.status().name());
        entity.setVersion(configuration.version());
        entity.setCreatedAt(configuration.createdAt());
        return entity;
    }
}
