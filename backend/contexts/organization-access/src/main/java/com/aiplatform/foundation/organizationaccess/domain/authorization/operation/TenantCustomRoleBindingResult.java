package com.aiplatform.foundation.organizationaccess.domain.authorization.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import java.time.Instant;
import java.util.Objects;

/** 自定义角色绑定完成后需要原子提交的成员修订与绑定事实。 */
public record TenantCustomRoleBindingResult(
        TenantMembership revisedMembership,
        TenantCustomRoleBinding binding,
        Instant occurredAt) {

    public TenantCustomRoleBindingResult {
        revisedMembership = Objects.requireNonNull(revisedMembership, "修订成员不能为空");
        binding = Objects.requireNonNull(binding, "自定义角色绑定不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
    }
}
