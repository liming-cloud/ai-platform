package com.aiplatform.identity.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * 登录会话聚合根，用于审计登录态创建、失效和租户隔离。
 */
public record LoginSession(
        TenantId tenantId,
        String sessionId,
        String userId,
        AccessToken accessToken,
        Instant createdAt,
        boolean active
) implements AggregateRoot<String> {
    public LoginSession {
        tenantId = requireValue(tenantId, "租户 ID");
        sessionId = requireText(sessionId, "会话 ID");
        userId = requireText(userId, "用户 ID");
        accessToken = requireValue(accessToken, "访问令牌");
        createdAt = requireValue(createdAt, "创建时间");
    }

    public boolean canBeUsedAt(Instant now) {
        now = requireValue(now, "校验时间");
        return active && !accessToken.isExpired(now);
    }

    @Override
    public String id() {
        return sessionId;
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
