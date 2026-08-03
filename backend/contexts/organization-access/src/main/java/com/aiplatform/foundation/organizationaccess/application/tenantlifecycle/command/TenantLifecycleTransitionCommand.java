package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleAction;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 平台对一个租户生命周期执行的显式业务操作。
 */
public record TenantLifecycleTransitionCommand(
        TenantId tenantId,
        TenantLifecycleAction action) {

    public TenantLifecycleTransitionCommand {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        action = Objects.requireNonNull(action, "租户生命周期操作不能为空");
    }
}
