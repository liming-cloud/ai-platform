package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleErrorCode;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;

/**
 * 判定同一人员主体能否进入平台或租户活动范围。
 *
 * <p>调用方必须先锁定共同的 `SubjectIdentity`，再提供另一范围的当前事实；候选过滤不能替代本规则。</p>
 */
public final class PersonnelScopeEligibility {

    private PersonnelScopeEligibility() {
    }

    /**
     * 确认主体可以进入租户人员范围。
     *
     * @throws OrganizationAccessRuleViolation 当主体仍拥有活动平台管理员绑定时拒绝。
     */
    public static void requireTenantScope(boolean hasActivePlatformAdministrator) {
        if (hasActivePlatformAdministrator) {
            throw new OrganizationAccessRuleViolation(
                    OrganizationAccessRuleErrorCode.PERSONNEL_SCOPE_CONFLICT);
        }
    }

    /**
     * 确认主体可以进入平台人员范围。
     *
     * @throws OrganizationAccessRuleViolation 当主体仍拥有启用租户成员资格时拒绝。
     */
    public static void requirePlatformScope(long enabledTenantMemberships) {
        if (enabledTenantMemberships < 0L) {
            throw new IllegalArgumentException("启用租户成员数量不能为负数");
        }
        if (enabledTenantMemberships > 0L) {
            throw new OrganizationAccessRuleViolation(
                    OrganizationAccessRuleErrorCode.PERSONNEL_SCOPE_CONFLICT);
        }
    }
}
