package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.DomainEntity;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * 用户角色授权实体，支持按资源范围和有效期生成权限快照。
 */
public record UserRoleGrant(
        TenantId tenantId,
        String grantId,
        String userId,
        String roleId,
        ResourceScope scope,
        Instant expiresAt,
        boolean enabled
) implements DomainEntity<String> {
    public UserRoleGrant {
        tenantId = requireValue(tenantId, "租户 ID");
        grantId = requireText(grantId, "授权 ID");
        userId = requireText(userId, "用户 ID");
        roleId = requireText(roleId, "角色 ID");
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
