package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleRevocationResult;
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

    /**
     * 撤销已存在的内置角色绑定并推进成员权限修订。
     *
     * <p>目标撤销后的有效访问管理能力与其他启用访问管理员数量是受锁读取事实；它们跨越成员聚合根，
     * 因而由应用层提供，本领域服务只裁决最后访问管理员不变量。</p>
     *
     * @throws IllegalArgumentException 当目标成员不属于当前租户或其他有效管理员数量为负数时拒绝。
     * @throws IllegalStateException 当目标成员未启用，或撤销会移除租户最后一个访问管理员时拒绝。
     */
    public static TenantRoleRevocationResult revoke(
            TenantMembership membership,
            TenantId tenantId,
            TenantBuiltInRole role,
            boolean retainsAccessManageAfterRevocation,
            long otherEnabledAccessManagers,
            Instant occurredAt) {
        membership = Objects.requireNonNull(membership, "目标成员资格不能为空");
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        role = Objects.requireNonNull(role, "内置角色不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!membership.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("目标成员不属于当前租户");
        }
        if (membership.status() != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("未启用成员不能撤销角色");
        }
        if (otherEnabledAccessManagers < 0L) {
            throw new IllegalArgumentException("其他有效访问管理员数量不能为负数");
        }
        if (role == TenantBuiltInRole.TENANT_ACCESS_ADMINISTRATOR
                && !retainsAccessManageAfterRevocation
                && otherEnabledAccessManagers == 0L) {
            throw new IllegalStateException("不能撤销租户最后一个访问管理员角色");
        }
        return new TenantRoleRevocationResult(
                membership.revisePermissions(), tenantId, membership.id(), role, occurredAt);
    }
}
