package com.aiplatform.foundation.organizationaccess.application.authorization.command;

import java.util.Objects;

/**
 * 当前租户管理员提交的成员角色绑定目标，不包含租户、当前主体或权限事实。
 */
public record TenantRoleBindingCommand(long membershipId, String roleCode) {

    public TenantRoleBindingCommand {
        if (membershipId <= 0L) {
            throw new IllegalArgumentException("目标成员标识必须为正数");
        }
        roleCode = Objects.requireNonNull(roleCode, "内置角色编码不能为空");
        if (roleCode.isBlank()) {
            throw new IllegalArgumentException("内置角色编码不能为空白");
        }
    }
}
