package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/** 成员与活动自定义角色之间不可变的同租户绑定事实。 */
public record TenantCustomRoleBinding(
        TenantId tenantId,
        MemberId membershipId,
        TenantCustomRoleId customRoleId,
        Instant boundAt) implements ValueObject {

    public TenantCustomRoleBinding {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        membershipId = Objects.requireNonNull(membershipId, "成员标识不能为空");
        customRoleId = Objects.requireNonNull(customRoleId, "租户自定义角色标识不能为空");
        boundAt = Objects.requireNonNull(boundAt, "绑定时间不能为空");
    }
}
