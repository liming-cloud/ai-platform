package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;

/**
 * 角色聚合根，承载租户内 RBAC 角色的启停状态和稳定编码。
 */
public record Role(
        TenantId tenantId,
        String roleId,
        String roleCode,
        String roleName,
        boolean enabled
) implements AggregateRoot<String> {
    public Role {
        tenantId = requireValue(tenantId, "租户 ID");
        roleId = requireText(roleId, "角色 ID");
        roleCode = requireText(roleCode, "角色编码");
        roleName = requireText(roleName, "角色名称");
    }

    @Override
    public String id() {
        return roleId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
