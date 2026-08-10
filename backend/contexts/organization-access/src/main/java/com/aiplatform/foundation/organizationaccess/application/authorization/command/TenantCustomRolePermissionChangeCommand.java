package com.aiplatform.foundation.organizationaccess.application.authorization.command;

import java.util.Set;

/** 修改当前租户自定义角色权限集合的命令。 */
public record TenantCustomRolePermissionChangeCommand(long customRoleId, Set<String> permissionCodes) {

    public TenantCustomRolePermissionChangeCommand {
        if (customRoleId <= 0L) {
            throw new IllegalArgumentException("自定义角色标识必须为正整数");
        }
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
