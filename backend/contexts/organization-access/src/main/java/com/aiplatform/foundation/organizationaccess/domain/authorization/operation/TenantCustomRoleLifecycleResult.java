package com.aiplatform.foundation.organizationaccess.domain.authorization.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 自定义角色变更后需要原子提交的角色事实与受影响成员权限修订。 */
public record TenantCustomRoleLifecycleResult(
        TenantCustomRole role,
        List<TenantMembership> revisedMemberships,
        Instant occurredAt) {

    public TenantCustomRoleLifecycleResult {
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        revisedMemberships = List.copyOf(Objects.requireNonNull(revisedMemberships, "修订成员不能为空"));
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        for (var membership : revisedMemberships) {
            if (!role.tenantId().equals(Objects.requireNonNull(membership, "修订成员不能为空").tenantId())) {
                throw new IllegalArgumentException("修订成员与自定义角色不属于同一租户");
            }
        }
    }
}
