package com.aiplatform.foundation.organizationaccess.domain.member.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleErrorCode;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import java.util.Objects;

/** 租户管理员治理普通成员时必须遵守的目标与角色边界。 */
public final class TenantMemberGovernance {

    private TenantMemberGovernance() {
    }

    /**
     * 确认目标是当前租户的普通成员。
     *
     * @throws OrganizationAccessRuleViolation 当目标是租户管理员时拒绝。
     */
    public static void requireOrdinaryTarget(TenantMembership membership) {
        membership = Objects.requireNonNull(membership, "目标成员不能为空");
        if (membership.role() != TenantMembershipRole.TENANT_USER) {
            throw new OrganizationAccessRuleViolation(
                    OrganizationAccessRuleErrorCode.TENANT_ADMINISTRATOR_PLATFORM_OWNED);
        }
    }

    /**
     * 确认租户成员治理命令只处理普通用户固定角色。
     *
     * @throws OrganizationAccessRuleViolation 当命令试图分配或撤销租户管理员角色时拒绝。
     */
    public static void requireOrdinaryRole(TenantBuiltInRole role) {
        role = Objects.requireNonNull(role, "租户角色不能为空");
        if (role != TenantBuiltInRole.TENANT_USER) {
            throw new OrganizationAccessRuleViolation(
                    OrganizationAccessRuleErrorCode.TENANT_ADMINISTRATOR_PLATFORM_OWNED);
        }
    }
}
