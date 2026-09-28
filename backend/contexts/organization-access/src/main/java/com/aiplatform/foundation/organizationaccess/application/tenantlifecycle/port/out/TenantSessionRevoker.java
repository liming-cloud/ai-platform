package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 只撤销指定租户的租户范围会话；不触及平台会话和其他租户。 */
@FunctionalInterface
public interface TenantSessionRevoker {
    void revokeTenantSessions(TenantId tenantId);
}
