package com.aiplatform.identity.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Set;

/**
 * 企业用户聚合根，承载登录资格、组织归属和平台角色集合。
 */
public record EnterpriseUser(
        TenantId tenantId,
        String userId,
        String loginName,
        String displayName,
        Set<String> roleCodes,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) implements AggregateRoot<String> {
    public EnterpriseUser {
        tenantId = requireValue(tenantId, "租户 ID");
        userId = requireText(userId, "用户 ID");
        loginName = requireText(loginName, "登录名");
        displayName = requireText(displayName, "用户显示名");
        roleCodes = normalizeRoleCodes(roleCodes);
        status = requireValue(status, "用户状态");
        createdAt = requireValue(createdAt, "创建时间");
        updatedAt = requireValue(updatedAt, "更新时间");
    }

    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public String id() {
        return userId;
    }

    private static Set<String> normalizeRoleCodes(Set<String> values) {
        Set<String> normalized = requireValue(values, "角色编码集合").stream()
                .map(value -> requireText(value, "角色编码"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("角色编码集合不能为空");
        }
        return normalized;
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
