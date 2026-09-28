package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLoginSecurityPolicyEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginSecurityPolicyMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.Optional;

/** 只按目标租户和预期版本替换单行策略。 */
public final class PostgresTenantLoginSecurityPolicyStore implements TenantLoginSecurityPolicyStore {
    private final TenantLoginSecurityPolicyMapper mapper;

    public PostgresTenantLoginSecurityPolicyStore(TenantLoginSecurityPolicyMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "登录策略映射器不能为空");
    }

    @Override
    public Optional<TenantLoginSecurityPolicy> find(long tenantId) {
        return Optional.ofNullable(mapper.selectById(tenantId)).map(TenantLoginSecurityPolicyEntity::toDomain);
    }

    @Override
    public boolean replace(TenantLoginSecurityPolicy next, long expectedVersion) {
        return mapper.update(TenantLoginSecurityPolicyEntity.from(next),
                new LambdaUpdateWrapper<TenantLoginSecurityPolicyEntity>()
                        .eq(TenantLoginSecurityPolicyEntity::getTenantId, next.tenantId().value())
                        .eq(TenantLoginSecurityPolicyEntity::getVersion, expectedVersion)) == 1;
    }
}
