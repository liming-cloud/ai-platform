package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import java.util.Objects;

/** 租户安全策略的公开结果，不包含主体、失败计数和会话材料。 */
public record TenantLoginSecurityPolicyResult(int sessionHours, int failedAttempts, int lockMinutes, long version) {
    public static TenantLoginSecurityPolicyResult from(TenantLoginSecurityPolicy policy) {
        Objects.requireNonNull(policy, "租户登录策略不能为空");
        return new TenantLoginSecurityPolicyResult(policy.sessionHours(), policy.failedAttempts(),
                policy.lockMinutes(), policy.version());
    }
}
