package com.aiplatform.foundation.organizationaccess.domain.authorization.operation;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Instant;
import java.util.Objects;

/**
 * 角色绑定领域操作的不可变提交结果。
 */
public record TenantRoleBindingResult(
        TenantMembership revisedMembership,
        TenantRoleBinding binding,
        Instant occurredAt) implements ValueObject {

    public TenantRoleBindingResult {
        revisedMembership = Objects.requireNonNull(revisedMembership, "修订成员资格不能为空");
        binding = Objects.requireNonNull(binding, "角色绑定不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!revisedMembership.tenantId().equals(binding.tenantId())
                || !revisedMembership.id().equals(binding.membershipId())
                || !occurredAt.equals(binding.boundAt())) {
            throw new IllegalArgumentException("角色绑定结果不满足成员归属与操作时间一致性");
        }
    }
}
