package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantRoleBindingView;
import java.util.Objects;

/**
 * 已提交角色绑定的最小公开投影，不返回权限全集或会话材料。
 */
public record TenantRoleBindingResponse(
        String membershipId,
        String role,
        long permissionVersion,
        String boundAt) {

    /**
     * 从已提交的领域操作结果投影 HTTP 响应。
     */
    public static TenantRoleBindingResponse from(TenantRoleBindingView binding) {
        binding = Objects.requireNonNull(binding, "角色绑定视图不能为空");
        return new TenantRoleBindingResponse(
                Long.toString(binding.membershipId()),
                binding.roleCode(),
                binding.permissionVersion(),
                binding.boundAt().toString());
    }
}
