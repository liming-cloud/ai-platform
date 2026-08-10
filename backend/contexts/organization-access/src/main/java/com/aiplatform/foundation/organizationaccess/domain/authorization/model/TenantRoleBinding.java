package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 成员在单一租户内取得内置角色的不可变绑定事实。
 */
public record TenantRoleBinding(
        TenantId tenantId,
        MemberId membershipId,
        TenantBuiltInRole role,
        Instant boundAt) implements ValueObject {

    public TenantRoleBinding {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        role = Objects.requireNonNull(role, "内置角色不能为空");
        boundAt = Objects.requireNonNull(boundAt, "绑定时间不能为空");
    }
}
