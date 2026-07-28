package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL 授权适配器；活动授权查询不会读取凭据材料。
 */
public final class PostgresModelConfigurationAccessRepository
        implements ModelConfigurationAccessRepository {

    private final ModelConfigurationAccessMapper mapper;

    public PostgresModelConfigurationAccessRepository(ModelConfigurationAccessMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型配置授权映射器不能为空");
    }

    @Override
    public Optional<ModelConfigurationAccessGrant> findById(ModelConfigurationAccessGrantId id) {
        Objects.requireNonNull(id, "模型配置授权标识不能为空");
        return Optional.ofNullable(mapper.findById(id.value())).map(row ->
                ModelConfigurationAccessGrant.reconstitute(
                        new ModelConfigurationAccessGrantId(row.id()),
                        new ModelConfigurationId(row.modelConfigurationId()),
                        new TenantId(row.tenantId()),
                        row.membershipId() == null ? null : new ModelConfigurationOwnerId(row.membershipId()),
                        ModelConfigurationAccessGrantStatus.valueOf(row.status()),
                        row.version(), row.createdAt(), row.revokedAt()));
    }

    @Override
    public boolean hasActiveGrant(
            ModelConfigurationId configurationId, TenantId tenantId, ModelConfigurationOwnerId membershipId) {
        return mapper.hasActiveGrant(configurationId.value(), tenantId.value(), membershipId.value());
    }

    @Override
    public void save(ModelConfigurationAccessGrant grant) {
        Objects.requireNonNull(grant, "模型配置授权不能为空");
        mapper.insert(grant.id().value(), grant.modelConfigurationId().value(),
                grant.granteeTenantId().value(),
                grant.granteeMembershipId() == null ? null : grant.granteeMembershipId().value(),
                grant.status().name(), grant.version(), grant.createdAt());
    }

    @Override
    public void update(ModelConfigurationAccessGrant grant) {
        Objects.requireNonNull(grant, "模型配置授权不能为空");
        if (!mapper.revoke(grant.id().value(), grant.version(), grant.version() - 1, grant.revokedAt())) {
            throw new IllegalStateException("模型配置授权版本已经变化");
        }
    }
}
