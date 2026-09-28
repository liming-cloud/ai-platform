package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import java.util.Optional;

/** 每租户登录安全策略的版本化事实；缺失事实不得隐式补默认值。 */
public interface TenantLoginSecurityPolicyStore {
    /** 缺失策略作为独立失败事实交给用例处理，不隐式补默认值。 */
    Optional<TenantLoginSecurityPolicy> find(long tenantId);

    /** 只在当前版本匹配时完整替换策略。 */
    boolean replace(TenantLoginSecurityPolicy next, long expectedVersion);
}
