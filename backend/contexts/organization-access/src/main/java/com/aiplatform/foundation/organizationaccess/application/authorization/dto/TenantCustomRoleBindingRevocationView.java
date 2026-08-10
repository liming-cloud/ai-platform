package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingRevocationResult;
import java.time.Instant;
import java.util.Objects;

/** 自定义角色解绑完成后可安全返回的最小摘要。 */
public record TenantCustomRoleBindingRevocationView(
        long membershipId,
        long customRoleId,
        long permissionVersion,
        Instant occurredAt) {

    public TenantCustomRoleBindingRevocationView {
        if (membershipId <= 0L || customRoleId <= 0L || permissionVersion < 0L) {
            throw new IllegalArgumentException("自定义角色解绑摘要不正确");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "解绑时间不能为空");
    }

    public static TenantCustomRoleBindingRevocationView from(
            TenantCustomRoleBindingRevocationResult result) {
        result = Objects.requireNonNull(result, "自定义角色解绑结果不能为空");
        return new TenantCustomRoleBindingRevocationView(
                result.membershipId().value(),
                result.customRoleId().value(),
                result.revisedMembership().permissionVersion(),
                result.occurredAt());
    }
}
