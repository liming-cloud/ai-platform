package com.aiplatform.identity.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * 用户密码凭证聚合，独立于用户资料读取，避免敏感哈希进入普通用户查询模型。
 */
public record PasswordCredential(
        TenantId tenantId,
        String userId,
        PasswordHash passwordHash,
        Instant updatedAt
) implements AggregateRoot<String> {
    public PasswordCredential {
        tenantId = requireValue(tenantId, "租户 ID");
        userId = requireText(userId, "用户 ID");
        passwordHash = requireValue(passwordHash, "密码哈希");
        updatedAt = requireValue(updatedAt, "更新时间");
    }

    @Override
    public String id() {
        return userId;
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
