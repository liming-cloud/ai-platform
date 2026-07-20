package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.Locale;

/**
 * 资源 ACL 聚合根，承载资源实例上的用户、角色或部门授权快照。
 */
public record ResourceAcl(
        TenantId tenantId,
        String aclId,
        ResourceScope resourceScope,
        String subjectType,
        String subjectId,
        PermissionCode permissionCode,
        String effect,
        Instant expiresAt,
        boolean enabled
) implements AggregateRoot<String> {
    public ResourceAcl {
        tenantId = requireValue(tenantId, "租户 ID");
        aclId = requireText(aclId, "ACL ID");
        resourceScope = requireValue(resourceScope, "资源范围");
        subjectType = requireText(subjectType, "授权主体类型");
        subjectId = requireText(subjectId, "授权主体 ID");
        permissionCode = requireValue(permissionCode, "权限编码");
        effect = requireEffect(effect);
    }

    public boolean activeAt(Instant now) {
        requireValue(now, "校验时间");
        return enabled && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean denies() {
        return "deny".equalsIgnoreCase(effect);
    }

    public boolean allows() {
        return "allow".equalsIgnoreCase(effect);
    }

    @Override
    public String id() {
        return aclId;
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

    private static String requireEffect(String value) {
        String normalized = requireText(value, "授权效果").toLowerCase(Locale.ROOT);
        if (!"allow".equals(normalized) && !"deny".equals(normalized)) {
            throw new IllegalArgumentException("授权效果只能是 allow 或 deny");
        }
        return normalized;
    }
}
