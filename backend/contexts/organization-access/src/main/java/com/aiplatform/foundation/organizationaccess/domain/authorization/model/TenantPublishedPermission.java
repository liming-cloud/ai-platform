package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import java.util.Set;

/**
 * 已拥有真实租户授权点和控制台任务的权限目录。
 *
 * <p>此目录不是第二套 ACL；目标业务服务仍独立从可信上下文检查这些权限。未拥有调用方的候选职责不得加入。</p>
 */
public enum TenantPublishedPermission {
    ACCESS_MANAGE("access:manage"),
    KNOWLEDGE_READ("knowledge:read"),
    KNOWLEDGE_WRITE("knowledge:write"),
    MODEL_READ("model:read"),
    MODEL_WRITE("model:write"),
    ASSISTANT_READ("assistant:read"),
    ASSISTANT_WRITE("assistant:write"),
    USAGE_READ("usage:read"),
    AUDIT_READ("audit:read"),
    ASSISTANT_QUERY("assistant:query");

    private final PermissionCode permission;

    TenantPublishedPermission(String permission) {
        this.permission = new PermissionCode(permission);
    }

    /** 返回目录发布的稳定权限编码。 */
    public PermissionCode permission() {
        return permission;
    }

    /** 判断一个权限集合是否完全来自当前租户发布目录。 */
    public static boolean containsAll(Set<PermissionCode> permissions) {
        return Set.of(values()).stream().map(TenantPublishedPermission::permission)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                .containsAll(permissions);
    }
}
