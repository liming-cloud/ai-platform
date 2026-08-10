package com.aiplatform.foundation.organizationaccess.application.authorization.command;

/** 解除当前租户成员一项自定义角色绑定的命令。 */
public record TenantCustomRoleBindingRevocationCommand(long membershipId, long customRoleId) {

    public TenantCustomRoleBindingRevocationCommand {
        if (membershipId <= 0L || customRoleId <= 0L) {
            throw new IllegalArgumentException("成员和自定义角色标识必须为正整数");
        }
    }
}
