package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingResult;
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
}
