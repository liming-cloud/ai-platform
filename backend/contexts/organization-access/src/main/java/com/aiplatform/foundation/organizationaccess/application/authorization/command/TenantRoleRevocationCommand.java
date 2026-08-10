package com.aiplatform.foundation.organizationaccess.application.authorization.command;

/** 请求撤销当前租户成员的一项内置角色绑定。 */
public record TenantRoleRevocationCommand(long membershipId, String roleCode) {

    public TenantRoleRevocationCommand {
        if (membershipId < 1L) {
            throw new IllegalArgumentException("成员标识必须为正数");
        }
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("撤销角色不能为空");
        }
    }
}
