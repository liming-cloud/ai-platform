package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

import java.util.Set;

/** 租户目录的有界字面查询条件，不承载身份、排序或客户端声明的权限。 */
public record TenantDirectoryFilter(String query, String status) {

    /**
     * 规范化只读查询；通配符保持字面语义，由持久化适配器转义。
     *
     * @throws IllegalArgumentException 当查询超长、含空字符或状态不属于生命周期时拒绝。
     */
    public TenantDirectoryFilter {
        query = query == null ? "" : query.strip();
        status = status == null ? "" : status.strip();
        if (query.length() > 64 || query.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("租户搜索最多 64 个字符且不能包含空字符");
        }
        if (!Set.of("", "PROVISIONING", "ACTIVE", "SUSPENDED").contains(status)) {
            throw new IllegalArgumentException("租户生命周期筛选状态不正确");
        }
    }
}
