package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleBindingResult;
import java.time.Instant;
import java.util.Objects;

/**
 * 已提交角色绑定的应用层读取投影，不携带领域对象或会话材料。
 */
public record TenantRoleBindingView(
        long membershipId,
        String roleCode,
        long permissionVersion,
        Instant boundAt) {

    /**
     * 从已验证的领域操作结果创建稳定应用视图。
     */
    public static TenantRoleBindingView from(TenantRoleBindingResult result) {
        result = Objects.requireNonNull(result, "角色绑定结果不能为空");
        return new TenantRoleBindingView(
                result.binding().membershipId().value(),
                result.binding().role().name(),
                result.revisedMembership().permissionVersion(),
                result.binding().boundAt());
    }
}
