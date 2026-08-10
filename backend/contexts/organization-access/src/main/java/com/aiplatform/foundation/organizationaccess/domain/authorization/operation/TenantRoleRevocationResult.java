package com.aiplatform.foundation.organizationaccess.domain.authorization.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 内置角色撤销的不可变领域操作结果。
 *
 * <p>结果只包含被撤销的角色来源和已推进权限修订的成员资格，供基础设施在同一事务删除绑定并写回成员。
 * 不携带角色完整策略、会话或凭据。</p>
 */
public record TenantRoleRevocationResult(
        TenantMembership revisedMembership,
        TenantId tenantId,
        MemberId membershipId,
        TenantBuiltInRole role,
        Instant revokedAt) implements ValueObject {

    public TenantRoleRevocationResult {
        revisedMembership = Objects.requireNonNull(revisedMembership, "修订成员资格不能为空");
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        role = Objects.requireNonNull(role, "撤销角色不能为空");
        revokedAt = Objects.requireNonNull(revokedAt, "撤销时间不能为空");
        if (!revisedMembership.tenantId().equals(tenantId)
                || !revisedMembership.id().equals(membershipId)) {
            throw new IllegalArgumentException("角色撤销结果不满足成员归属一致性");
        }
    }
}
