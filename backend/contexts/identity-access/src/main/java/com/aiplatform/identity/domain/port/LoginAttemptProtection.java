package com.aiplatform.identity.domain.port;

import com.aiplatform.shared.kernel.TenantId;

/**
 * 多实例共享的登录失败防护端口，状态由基础设施 adapter 持有。
 */
public interface LoginAttemptProtection {
    LoginAttemptDecision inspect(TenantId tenantId, String loginName);

    LoginAttemptDecision recordFailure(TenantId tenantId, String loginName);

    LoginAttemptDecision reset(TenantId tenantId, String loginName);
}
