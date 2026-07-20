package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;

/**
 * 权限聚合根，权限编码由权限目录统一维护，业务代码不得散落硬编码。
 */
public record Permission(
        TenantId tenantId,
        String permissionId,
        PermissionCode code,
        String permissionName,
        boolean enabled
) implements AggregateRoot<String> {
    public Permission {
        tenantId = requireValue(tenantId, "租户 ID");
        permissionId = requireText(permissionId, "权限 ID");
        code = requireValue(code, "权限编码");
        permissionName = requireText(permissionName, "权限名称");
    }

    @Override
    public String id() {
        return permissionId;
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
