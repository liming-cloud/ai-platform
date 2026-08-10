package com.aiplatform.foundation.organizationaccess.application.authorization.command;

/** 停用当前租户一项自定义角色的命令。 */
public record TenantCustomRoleDisableCommand(long customRoleId) {

    public TenantCustomRoleDisableCommand {
        if (customRoleId <= 0L) {
            throw new IllegalArgumentException("自定义角色标识必须为正整数");
        }
    }
}
