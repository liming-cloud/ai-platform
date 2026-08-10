package com.aiplatform.foundation.organizationaccess.application.authorization.command;

/** 为当前租户成员绑定已存在自定义角色的应用命令。 */
public record TenantCustomRoleBindingCommand(long membershipId, long customRoleId) {

    public TenantCustomRoleBindingCommand {
        if (membershipId <= 0L || customRoleId <= 0L) {
            throw new IllegalArgumentException("成员和自定义角色标识必须为正数");
        }
    }
}
