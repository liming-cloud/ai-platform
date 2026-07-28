package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationPage;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ControlledModelCatalogEntryEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationEntity;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * PostgreSQL 受控目录与模型配置仓储，所有配置所有权由领域对象验证。
 */
public final class PostgresModelConfigurationRepository implements ModelConfigurationRepository {

    private final ControlledModelCatalogEntryMapper catalogMapper;
    private final ModelConfigurationMapper configurationMapper;

    public PostgresModelConfigurationRepository(
            ControlledModelCatalogEntryMapper catalogMapper,
            ModelConfigurationMapper configurationMapper) {
        this.catalogMapper = Objects.requireNonNull(catalogMapper, "受控模型目录映射器不能为空");
        this.configurationMapper = Objects.requireNonNull(configurationMapper, "模型配置映射器不能为空");
    }

    @Override
    public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
        Objects.requireNonNull(id, "受控模型目录条目标识不能为空");
        return Optional.ofNullable(catalogMapper.selectById(id.value())).map(row ->
                ControlledModelCatalogEntry.reconstitute(
                        new ControlledModelCatalogEntryId(row.getId()),
                        ProviderKind.valueOf(row.getProviderKind()),
                        URI.create(row.getEndpoint()),
                        row.getModelCode(),
                        ModelType.valueOf(row.getModelType()),
                        requireCapabilities(row),
                        ControlledModelCatalogEntryStatus.valueOf(row.getStatus()),
                        row.getVersion(),
                        row.getCreatedAt()));
    }

    @Override
    public void saveCatalogEntry(ControlledModelCatalogEntry entry) {
        Objects.requireNonNull(entry, "受控模型目录条目不能为空");
        catalogMapper.insert(toEntity(entry));
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
                || (row.getOutputLimit() == null && row.getEmbeddingDimension() == null)) {
            throw new IllegalStateException("受控模型目录条目缺少运行时能力快照，必须由平台重新发布");
        }
        return new ModelRuntimeCapabilities(
                row.getContextWindow(), row.getOutputLimit(), row.getEmbeddingDimension());
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
