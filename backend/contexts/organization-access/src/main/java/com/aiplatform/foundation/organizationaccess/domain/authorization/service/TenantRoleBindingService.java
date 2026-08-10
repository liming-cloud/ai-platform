package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 维护成员角色绑定与权限修订一致性的无状态领域服务。
 */
public final class TenantRoleBindingService {

    private TenantRoleBindingService() {
    }

    /**
     * 为已启用成员建立内置角色绑定。
     *
     * @throws IllegalArgumentException 当目标成员不属于当前租户时拒绝。
     * @throws IllegalStateException 当目标成员未启用时拒绝。
     */
    public static TenantRoleBindingResult bind(
            TenantMembership membership,
            TenantId tenantId,
            TenantBuiltInRole role,
            Instant occurredAt) {
        membership = Objects.requireNonNull(membership, "目标成员资格不能为空");
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        role = Objects.requireNonNull(role, "内置角色不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!membership.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("目标成员不属于当前租户");
        }
        if (membership.status() != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("未启用成员不能绑定角色");
        }
        return new TenantRoleBindingResult(
                membership.revisePermissions(),
                new TenantRoleBinding(tenantId, membership.id(), role, occurredAt),
                occurredAt);
    }
}
