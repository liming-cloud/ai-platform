package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingResult;
import java.time.Instant;
import java.util.Objects;

/** 已提交自定义角色绑定的最小应用读取投影。 */
public record TenantCustomRoleBindingView(long membershipId, long customRoleId, long permissionVersion, Instant boundAt) {

    /** 从领域操作结果投影绑定结果。 */
    public static TenantCustomRoleBindingView from(TenantCustomRoleBindingResult result) {
        result = Objects.requireNonNull(result, "自定义角色绑定结果不能为空");
        return new TenantCustomRoleBindingView(result.binding().membershipId().value(),
                result.binding().customRoleId().value(), result.revisedMembership().permissionVersion(),
                result.binding().boundAt());
    }
}
