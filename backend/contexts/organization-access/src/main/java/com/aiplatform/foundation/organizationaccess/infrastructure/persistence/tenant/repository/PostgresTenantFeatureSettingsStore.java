package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantFeatureSettingsEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantFeatureSettingsMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.Optional;

/** 只对目标租户和预期版本执行单行功能配置写入。 */
public final class PostgresTenantFeatureSettingsStore implements TenantFeatureSettingsStore {
    private final TenantFeatureSettingsMapper mapper;

    public PostgresTenantFeatureSettingsStore(TenantFeatureSettingsMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "功能配置映射器不能为空");
    }

    @Override
    public Optional<TenantFeatureSettings> find(long tenantId) {
        return Optional.ofNullable(mapper.selectById(tenantId)).map(TenantFeatureSettingsEntity::toDomain);
    }

    @Override
    public boolean replace(TenantFeatureSettings next, long expectedVersion) {
        return mapper.update(TenantFeatureSettingsEntity.from(next),
                new LambdaUpdateWrapper<TenantFeatureSettingsEntity>()
                        .eq(TenantFeatureSettingsEntity::getTenantId, next.tenantId().value())
                        .eq(TenantFeatureSettingsEntity::getVersion, expectedVersion)) == 1;
    }
}
