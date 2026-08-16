package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import java.util.Set;

/**
 * 当前已发布且不可删除的租户内置角色模板。
 *
 * <p>新增模板必须同时拥有真实授权点、管理员任务和失败关闭测试；不能用候选角色名称预占权限。</p>
 */
public enum TenantBuiltInRole {

    /** 管理所属租户的成员、数据、模型、助手、评测、运营和问答。 */
    TENANT_ADMINISTRATOR(Set.of(
            new PermissionCode("access:manage"),
            new PermissionCode("knowledge:read"),
            new PermissionCode("knowledge:write"),
            new PermissionCode("model:read"),
            new PermissionCode("model:write"),
            new PermissionCode("assistant:read"),
            new PermissionCode("assistant:write"),
            new PermissionCode("usage:read"),
            new PermissionCode("audit:read"),
            new PermissionCode("assistant:query"))),

    /** 在已有资料权限内发起可信问答与反馈。 */
    TENANT_USER(Set.of(
            new PermissionCode("assistant:query"),
            new PermissionCode("knowledge:read")));

    private final Set<PermissionCode> permissions;

    TenantBuiltInRole(Set<PermissionCode> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    /** 返回该模板对已有后端授权点的固定权限集合。 */
    public Set<PermissionCode> permissions() {
        return permissions;
    }
}
