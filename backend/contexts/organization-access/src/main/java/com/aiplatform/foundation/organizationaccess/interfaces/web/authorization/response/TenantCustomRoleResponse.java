package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleView;
import java.util.List;
import java.util.Objects;

/** 已创建自定义角色的最小公开投影，不暴露成员绑定或会话状态。 */
public record TenantCustomRoleResponse(
        String id,
        String name,
        List<String> permissionCodes,
        long version,
        String status) {

    /** 从应用读取视图投影稳定的 HTTP 响应。 */
    public static TenantCustomRoleResponse from(TenantCustomRoleView role) {
        role = Objects.requireNonNull(role, "租户自定义角色视图不能为空");
        return new TenantCustomRoleResponse(
                Long.toString(role.id()),
                role.name(),
                role.permissionCodes().stream().sorted().toList(),
                role.version(),
                role.status());
    }
}
