package com.aiplatform.foundation.organizationaccess.application.authorization.command;

import java.util.Objects;
import java.util.Set;

/** 创建租户自定义角色的应用命令。 */
public record TenantCustomRoleCreationCommand(String name, Set<String> permissionCodes) {

    public TenantCustomRoleCreationCommand {
        name = Objects.requireNonNull(name, "角色名称不能为空");
        permissionCodes = Set.copyOf(Objects.requireNonNull(permissionCodes, "角色权限不能为空"));
    }
}
