package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

/**
 * 当前已发布且不可删除的租户内置角色模板。
 *
 * <p>新增模板必须同时拥有真实授权点、管理员任务和失败关闭测试；不能用候选角色名称预占权限。</p>
 */
public enum TenantBuiltInRole {

    /** 允许在所属租户内管理成员角色绑定。 */
    TENANT_ACCESS_ADMINISTRATOR(new PermissionCode("access:manage"));

    private final PermissionCode permission;

    TenantBuiltInRole(PermissionCode permission) {
        this.permission = permission;
    }

    public PermissionCode permission() {
        return permission;
    }
}
