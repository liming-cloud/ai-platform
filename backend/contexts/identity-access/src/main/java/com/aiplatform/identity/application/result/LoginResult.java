package com.aiplatform.identity.application.result;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 登录结果，返回调用方所需的最小用户身份、角色和令牌边界字段，不暴露领域值对象。
 */
public record LoginResult(
        TenantId tenantId,
        String userId,
        String displayName,
        Set<String> roleCodes,
        String accessToken,
        Instant accessTokenExpiresAt
) {
    public LoginResult {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        userId = requireText(userId, "用户 ID");
        displayName = requireText(displayName, "用户显示名");
        roleCodes = normalizeRoleCodes(roleCodes);
        accessToken = requireText(accessToken, "访问令牌");
        Objects.requireNonNull(accessTokenExpiresAt, "令牌过期时间不能为空");
    }

    private static Set<String> normalizeRoleCodes(Set<String> values) {
        Objects.requireNonNull(values, "角色编码集合不能为空");
        Set<String> normalized = values.stream()
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
}
