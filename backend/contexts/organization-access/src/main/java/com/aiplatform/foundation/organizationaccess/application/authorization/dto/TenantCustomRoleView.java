package com.aiplatform.foundation.organizationaccess.application.authorization.dto;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import java.util.Objects;
import java.util.Set;

/** 租户自定义角色的安全应用读取投影。 */
public record TenantCustomRoleView(
        long id,
        String name,
        Set<String> permissionCodes,
        long version,
        String status) {

    /** 从领域聚合根投影不含成员和会话信息的角色视图。 */
    public static TenantCustomRoleView from(TenantCustomRole role) {
        role = Objects.requireNonNull(role, "租户自定义角色不能为空");
        return new TenantCustomRoleView(
                role.id().value(),
                role.name(),
                role.permissions().stream().map(permission -> permission.value()).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                role.version(),
                role.status().name());
    }
}
