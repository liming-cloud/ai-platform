package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantQuotaSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantQuotaEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.Optional;

/** 在租户配额单表以预期版本条件更新，避免旧页面覆盖新设置。 */
public final class PostgresTenantQuotaSettingsStore implements TenantQuotaSettingsStore {

    private final TenantQuotaMapper mapper;

    public PostgresTenantQuotaSettingsStore(TenantQuotaMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "配额映射器不能为空");
    }

    @Override
    public Optional<TenantQuota> find(long tenantId) {
        return Optional.ofNullable(mapper.selectById(tenantId)).map(TenantQuotaEntity::toDomain);
    }

    @Override
    public boolean replace(TenantQuota next, long expectedVersion) {
        var row = TenantQuotaEntity.from(next);
        return mapper.update(row, new LambdaUpdateWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, next.tenantId().value())
                .eq(TenantQuotaEntity::getVersion, expectedVersion)) == 1;
    }
}
