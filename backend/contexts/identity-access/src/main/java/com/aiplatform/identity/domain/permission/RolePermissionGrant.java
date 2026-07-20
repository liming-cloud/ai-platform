package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.DomainEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * 角色权限授权实体，表达某个角色在资源范围内可使用的权限点。
 */
public record RolePermissionGrant(
        TenantId tenantId,
        String grantId,
        String roleId,
        String permissionId,
        ResourceScope scope,
        Instant expiresAt,
        boolean enabled
) implements DomainEntity<String> {
    public RolePermissionGrant {
        tenantId = requireValue(tenantId, "租户 ID");
        grantId = requireText(grantId, "授权 ID");
        roleId = requireText(roleId, "角色 ID");
        permissionId = requireText(permissionId, "权限 ID");
        scope = requireValue(scope, "资源范围");
    }

    public boolean activeAt(Instant now) {
        requireValue(now, "校验时间");
        return enabled && (expiresAt == null || expiresAt.isAfter(now));
    }

    @Override
    public String id() {
        return grantId;
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
