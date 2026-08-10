package com.aiplatform.foundation.organizationaccess.domain.authorization.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 解除自定义角色绑定后需要原子提交的成员权限修订与删除目标。
 *
 * <p>角色绑定是历史授权事实；删除目标以稳定标识表示，避免应用层重新拼接租户、成员和角色归属。</p>
 */
public record TenantCustomRoleBindingRevocationResult(
        TenantMembership revisedMembership,
        TenantId tenantId,
        MemberId membershipId,
        TenantCustomRoleId customRoleId,
        Instant occurredAt) {

    public TenantCustomRoleBindingRevocationResult {
        revisedMembership = Objects.requireNonNull(revisedMembership, "修订成员不能为空");
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        customRoleId = Objects.requireNonNull(customRoleId, "自定义角色标识不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!revisedMembership.tenantId().equals(tenantId)
                || !revisedMembership.id().equals(membershipId)) {
            throw new IllegalArgumentException("自定义角色解绑结果与修订成员不一致");
        }
    }
}
