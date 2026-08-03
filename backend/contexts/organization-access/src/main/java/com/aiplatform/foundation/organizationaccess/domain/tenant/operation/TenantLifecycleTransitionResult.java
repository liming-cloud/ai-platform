package com.aiplatform.foundation.organizationaccess.domain.tenant.operation;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/**
 * 一个合法生命周期迁移应原子提交的不可变结果。
 */
public record TenantLifecycleTransitionResult(
        TenantLifecycle lifecycle,
        TenantLifecycleChange change) implements ValueObject {

    public TenantLifecycleTransitionResult {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期不能为空");
        change = Objects.requireNonNull(change, "租户生命周期变化不能为空");
        if (!lifecycle.id().equals(change.tenantId())
                || lifecycle.status() != change.status()
                || lifecycle.version() != change.version()) {
            throw new IllegalArgumentException("生命周期迁移结果不满足根与状态事实一致性");
        }
    }
}
