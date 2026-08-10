package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleBindingView;
import java.util.Objects;

/** 已提交自定义角色绑定的最小公开投影，不返回角色权限全集或会话材料。 */
public record TenantCustomRoleBindingResponse(
        String membershipId,
        String customRoleId,
        long permissionVersion,
        String boundAt) {

    /** 从已提交的领域操作结果投影 HTTP 响应。 */
    public static TenantCustomRoleBindingResponse from(TenantCustomRoleBindingView binding) {
        binding = Objects.requireNonNull(binding, "自定义角色绑定视图不能为空");
        return new TenantCustomRoleBindingResponse(
                Long.toString(binding.membershipId()),
                Long.toString(binding.customRoleId()),
                binding.permissionVersion(),
                binding.boundAt().toString());
    }
}
