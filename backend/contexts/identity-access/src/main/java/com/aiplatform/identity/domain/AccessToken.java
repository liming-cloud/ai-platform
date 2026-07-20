package com.aiplatform.identity.domain;

import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;

/**
 * 登录令牌值对象，仅表达令牌生命周期，不承载具体 Sa-Token 实现。
 */
public record AccessToken(String tokenValue, Instant expiresAt) implements ValueObject {
    public AccessToken {
        tokenValue = requireText(tokenValue, "访问令牌");
        expiresAt = requireValue(expiresAt, "令牌过期时间");
    }

    public boolean isExpired(Instant now) {
        now = requireValue(now, "校验时间");
        return !expiresAt.isAfter(now);
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
