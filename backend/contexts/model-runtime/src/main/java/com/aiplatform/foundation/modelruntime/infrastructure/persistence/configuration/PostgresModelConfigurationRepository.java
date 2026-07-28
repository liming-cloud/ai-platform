package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * PostgreSQL 受控目录与模型配置仓储，所有配置所有权由领域对象验证。
 */
public final class PostgresModelConfigurationRepository implements ModelConfigurationRepository {

    private final ModelConfigurationMapper mapper;

    public PostgresModelConfigurationRepository(ModelConfigurationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型配置映射器不能为空");
    }

    @Override
    public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
        Objects.requireNonNull(id, "受控模型目录条目标识不能为空");
        return Optional.ofNullable(mapper.findCatalogEntry(id.value())).map(row ->
                ControlledModelCatalogEntry.reconstitute(
                        new ControlledModelCatalogEntryId(row.id()),
                        ProviderKind.valueOf(row.providerKind()),
                        URI.create(row.endpoint()),
                        row.modelCode(),
                        ModelType.valueOf(row.modelType()),
                        ControlledModelCatalogEntryStatus.valueOf(row.status()),
                        row.version(),
                        row.createdAt()));
    }

    @Override
    public void saveCatalogEntry(ControlledModelCatalogEntry entry) {
        Objects.requireNonNull(entry, "受控模型目录条目不能为空");
        mapper.insertCatalogEntry(
                entry.id().value(),
                entry.providerKind().name(),
                entry.endpoint().toString(),
                entry.modelCode(),
                entry.modelType().name(),
                entry.status().name(),
                entry.version(),
                entry.createdAt());
    }

    @Override
    public void updateCatalogEntry(ControlledModelCatalogEntry entry) {
        Objects.requireNonNull(entry, "受控模型目录条目不能为空");
        if (!mapper.updateCatalogEntry(
                entry.id().value(), entry.status().name(), entry.version(), entry.version() - 1)) {
            throw new IllegalStateException("受控模型目录条目版本已经变化");
        }
    }

    @Override
    public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
        Objects.requireNonNull(id, "模型配置标识不能为空");
        return Optional.ofNullable(mapper.findConfiguration(id.value())).map(row ->
                ModelConfiguration.reconstitute(
                        new ModelConfigurationId(row.id()),
                        ModelConfigurationScope.valueOf(row.scope()),
                        row.tenantId() == null ? null : new TenantId(row.tenantId()),
                        row.ownerMembershipId() == null ? null
                                : new ModelConfigurationOwnerId(row.ownerMembershipId()),
                        row.displayName(),
                        new ControlledModelCatalogEntryId(row.catalogEntryId()),
                        ModelConfigurationStatus.valueOf(row.status()),
                        row.version(),
                        row.createdAt()));
    }

    @Override
    public void updateConfiguration(ModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "模型配置不能为空");
        if (!mapper.updateConfiguration(
                configuration.id().value(), configuration.status().name(),
                configuration.version(), configuration.version() - 1)) {
            throw new IllegalStateException("模型配置版本已经变化");
        }
    }

    @Override
    public void save(ModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "模型配置不能为空");
        mapper.insertConfiguration(
                configuration.id().value(),
                configuration.scope().name(),
                configuration.tenantId() == null ? null : configuration.tenantId().value(),
                configuration.ownerMembershipId() == null ? null : configuration.ownerMembershipId().value(),
                configuration.displayName(),
                configuration.catalogEntryId().value(),
                configuration.status().name(),
                configuration.version(),
                configuration.createdAt());
    }
}
