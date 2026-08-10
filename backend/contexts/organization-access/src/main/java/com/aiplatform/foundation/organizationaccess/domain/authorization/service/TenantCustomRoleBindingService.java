package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingRevocationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import java.time.Instant;
import java.util.Objects;

/** 维护活动自定义角色与成员权限修订一致性的无状态领域服务。 */
public final class TenantCustomRoleBindingService {

    private TenantCustomRoleBindingService() {
    }

    /**
     * 将一个活动角色绑定至同租户的已启用成员。
     *
     * @throws IllegalArgumentException 当角色和成员不属于同一租户时拒绝。
     * @throws IllegalStateException 当成员未启用或角色已停用时拒绝。
     */
    public static TenantCustomRoleBindingResult bind(
            TenantMembership membership,
            TenantCustomRole role,
            Instant occurredAt) {
        membership = Objects.requireNonNull(membership, "目标成员资格不能为空");
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!membership.tenantId().equals(role.tenantId())) {
            throw new IllegalArgumentException("目标成员与自定义角色不属于同一租户");
        }
        if (membership.status() != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("未启用成员不能绑定自定义角色");
        }
        if (!role.isActive()) {
            throw new IllegalStateException("停用自定义角色不能绑定成员");
        }
        return new TenantCustomRoleBindingResult(
                membership.revisePermissions(),
                new TenantCustomRoleBinding(
                        membership.tenantId(), membership.id(), role.id(), occurredAt),
                occurredAt);
    }

    /**
     * 解除活动自定义角色与已启用成员之间的绑定。
     *
     * <p>最后访问管理员守卫需要比较同租户其他角色来源，属于跨聚合事实，由应用层在提交前裁决。</p>
     *
     * @throws IllegalArgumentException 当成员与角色不属于同一租户时拒绝。
     * @throws IllegalStateException 当成员未启用或角色已停用时拒绝。
     */
    public static TenantCustomRoleBindingRevocationResult unbind(
            TenantMembership membership,
            TenantCustomRole role,
            boolean retainsAccessManageAfterRevocation,
            long otherEnabledAccessManagers,
            Instant occurredAt) {
        membership = Objects.requireNonNull(membership, "目标成员资格不能为空");
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!membership.tenantId().equals(role.tenantId())) {
            throw new IllegalArgumentException("目标成员与自定义角色不属于同一租户");
        }
        if (membership.status() != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("未启用成员不能解除自定义角色");
        }
        if (!role.isActive()) {
            throw new IllegalStateException("停用自定义角色不能解除活动绑定");
        }
        if (otherEnabledAccessManagers < 0L) {
            throw new IllegalArgumentException("其他有效访问管理员数量不能为负数");
        }
        if (role.permissions().contains(new com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode(
                "access:manage"))
                && !retainsAccessManageAfterRevocation
                && otherEnabledAccessManagers == 0L) {
            throw new IllegalStateException("不能撤销租户最后一个访问管理员角色");
        }
        return new TenantCustomRoleBindingRevocationResult(
                membership.revisePermissions(),
                membership.tenantId(),
                membership.id(),
                role.id(),
                occurredAt);
    }
}
