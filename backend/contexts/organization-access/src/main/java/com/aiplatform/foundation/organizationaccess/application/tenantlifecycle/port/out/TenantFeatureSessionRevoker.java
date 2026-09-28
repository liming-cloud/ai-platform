package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 配置改变时撤销目标租户所有旧租户会话；失败必须抛出以回滚配置事务。 */
@FunctionalInterface
public interface TenantFeatureSessionRevoker {
    void revokeTenantSessions(TenantId tenantId);
}
