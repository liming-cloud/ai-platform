package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleLifecycleResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 维护自定义角色生命周期与所有受影响成员权限修订一致性的无状态领域服务。 */
public final class TenantCustomRoleLifecycleService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private TenantCustomRoleLifecycleService() {
    }

    /**
     * 修改活动自定义角色的权限集合，并为所有受影响成员生成下一权限修订。
     *
     * @throws IllegalStateException 当移除访问管理能力会使有效访问管理员归零时拒绝。
     */
    public static TenantCustomRoleLifecycleResult changePermissions(
            TenantCustomRole role,
            Set<PermissionCode> permissions,
            List<TenantMembership> affectedMemberships,
            long enabledAccessManagersAfterChange,
            Instant occurredAt) {
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "角色权限不能为空"));
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        requireAccessManagerSafety(
                role.permissions().contains(ACCESS_MANAGE) && !permissions.contains(ACCESS_MANAGE),
                enabledAccessManagersAfterChange);
        return new TenantCustomRoleLifecycleResult(
                role.changePermissions(permissions, occurredAt),
                reviseAffectedMemberships(role, affectedMemberships),
                occurredAt);
    }

    /**
     * 停用活动自定义角色并使其所有受影响成员重新解析有效权限。
     *
     * @throws IllegalStateException 当停用会使有效访问管理员归零时拒绝。
     */
    public static TenantCustomRoleLifecycleResult disable(
            TenantCustomRole role,
            List<TenantMembership> affectedMemberships,
            long enabledAccessManagersAfterChange,
            Instant occurredAt) {
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        requireAccessManagerSafety(role.permissions().contains(ACCESS_MANAGE), enabledAccessManagersAfterChange);
        return new TenantCustomRoleLifecycleResult(
                role.disable(occurredAt),
                reviseAffectedMemberships(role, affectedMemberships),
                occurredAt);
    }

    private static List<TenantMembership> reviseAffectedMemberships(
            TenantCustomRole role,
            List<TenantMembership> memberships) {
        memberships = List.copyOf(Objects.requireNonNull(memberships, "受影响成员不能为空"));
        return memberships.stream()
                .map(membership -> {
                    membership = Objects.requireNonNull(membership, "受影响成员不能为空");
                    if (!role.tenantId().equals(membership.tenantId())) {
                        throw new IllegalArgumentException("受影响成员与自定义角色不属于同一租户");
                    }
                    return membership.revisePermissions();
                })
                .toList();
    }

    private static void requireAccessManagerSafety(
            boolean removesAccessManage,
            long enabledAccessManagersAfterChange) {
        if (enabledAccessManagersAfterChange < 0L) {
            throw new IllegalArgumentException("有效访问管理员数量不能为负数");
        }
        if (removesAccessManage && enabledAccessManagersAfterChange == 0L) {
            throw new IllegalStateException("不能移除租户最后一个访问管理员角色");
        }
    }
}
