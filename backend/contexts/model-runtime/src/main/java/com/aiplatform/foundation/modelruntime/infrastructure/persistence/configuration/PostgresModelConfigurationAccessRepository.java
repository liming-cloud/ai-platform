package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantStatus;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationAccessGrantEntity;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
        return Optional.ofNullable(mapper.selectById(id.value())).map(row ->
                ModelConfigurationAccessGrant.reconstitute(
                        new ModelConfigurationAccessGrantId(row.getId()),
                        new ModelConfigurationId(row.getModelConfigurationId()),
                        new TenantId(row.getTenantId()),
                        row.getMembershipId() == null ? null : new ModelConfigurationOwnerId(row.getMembershipId()),
                        ModelConfigurationAccessGrantStatus.valueOf(row.getStatus()),
                        row.getVersion(), row.getCreatedAt(), row.getRevokedAt()));
    }

    @Override
    public boolean hasActiveGrant(
            ModelConfigurationId configurationId, TenantId tenantId, ModelConfigurationOwnerId membershipId) {
        return mapper.selectCount(Wrappers.<ModelConfigurationAccessGrantEntity>lambdaQuery()
                .eq(ModelConfigurationAccessGrantEntity::getModelConfigurationId, configurationId.value())
                .eq(ModelConfigurationAccessGrantEntity::getTenantId, tenantId.value())
                .eq(ModelConfigurationAccessGrantEntity::getStatus, "ACTIVE")
                .and(query -> query.isNull(ModelConfigurationAccessGrantEntity::getMembershipId)
                        .or()
                        .eq(ModelConfigurationAccessGrantEntity::getMembershipId, membershipId.value()))) > 0;
    }

    @Override
    public void save(ModelConfigurationAccessGrant grant) {
        Objects.requireNonNull(grant, "模型配置授权不能为空");
        mapper.insert(toEntity(grant));
    }

    @Override
    public void update(ModelConfigurationAccessGrant grant) {
        Objects.requireNonNull(grant, "模型配置授权不能为空");
        if (mapper.update(null, Wrappers.<ModelConfigurationAccessGrantEntity>lambdaUpdate()
                .eq(ModelConfigurationAccessGrantEntity::getId, grant.id().value())
                .eq(ModelConfigurationAccessGrantEntity::getVersion, grant.version() - 1)
                .eq(ModelConfigurationAccessGrantEntity::getStatus, "ACTIVE")
                .set(ModelConfigurationAccessGrantEntity::getStatus, grant.status().name())
                .set(ModelConfigurationAccessGrantEntity::getVersion, grant.version())
                .set(ModelConfigurationAccessGrantEntity::getRevokedAt, grant.revokedAt())) != 1) {
            throw new IllegalStateException("模型配置授权版本已经变化");
        }
    }

    private static ModelConfigurationAccessGrantEntity toEntity(ModelConfigurationAccessGrant grant) {
        var entity = new ModelConfigurationAccessGrantEntity();
        entity.setId(grant.id().value());
        entity.setModelConfigurationId(grant.modelConfigurationId().value());
        entity.setTenantId(grant.granteeTenantId().value());
        entity.setMembershipId(grant.granteeMembershipId() == null ? null : grant.granteeMembershipId().value());
        entity.setStatus(grant.status().name());
        entity.setVersion(grant.version());
        entity.setCreatedAt(grant.createdAt());
        return entity;
    }
}
