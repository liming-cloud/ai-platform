package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import java.util.Set;

/**
 * 当前已发布且不可删除的租户内置角色模板。
 *
 * <p>新增模板必须同时拥有真实授权点、管理员任务和失败关闭测试；不能用候选角色名称预占权限。</p>
 */
public enum TenantBuiltInRole {

    /** 允许在所属租户内管理成员角色绑定。 */
    TENANT_ACCESS_ADMINISTRATOR(Set.of(new PermissionCode("access:manage"))),

    /** 管理所属租户知识空间、受治理数据源和索引版本。 */
    TENANT_KNOWLEDGE_MANAGER(Set.of(
            new PermissionCode("knowledge:read"),
            new PermissionCode("knowledge:write"))),

    /** 管理所属租户供应商连接、模型发现、部署和配置。 */
    TENANT_MODEL_MANAGER(Set.of(
            new PermissionCode("model:read"),
            new PermissionCode("model:write"))),

    /** 管理所属租户助手草稿、版本和现有受限评测入口。 */
    TENANT_ASSISTANT_OWNER(Set.of(
            new PermissionCode("assistant:read"),
            new PermissionCode("assistant:write"))),

    /** 读取所属租户的用量、脱敏审计和诊断摘要。 */
    TENANT_AUDITOR(Set.of(
            new PermissionCode("usage:read"),
            new PermissionCode("audit:read"))),

    /** 在已有资料权限内发起可信问答与反馈。 */
    TENANT_QUERY_USER(Set.of(
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
