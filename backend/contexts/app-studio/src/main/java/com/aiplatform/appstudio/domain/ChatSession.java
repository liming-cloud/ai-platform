package com.aiplatform.appstudio.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;

/**
 * AI 应用聊天会话聚合根，记录应用、用户和最近消息时间。
 */
public record ChatSession(
        TenantId tenantId,
        String sessionId,
        String applicationId,
        String releaseId,
        long releaseVersion,
        String userId,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) implements AggregateRoot<String> {
    public ChatSession {
        tenantId = requireValue(tenantId, "租户 ID");
        sessionId = requireText(sessionId, "会话 ID");
        applicationId = requireText(applicationId, "应用 ID");
        releaseId = requireText(releaseId, "发布版本 ID");
        if (releaseVersion < 1) {
            throw new IllegalArgumentException("发布版本号必须大于 0");
        }
        userId = requireText(userId, "用户 ID");
        title = requireText(title, "会话标题");
        createdAt = requireValue(createdAt, "创建时间");
        lastMessageAt = requireValue(lastMessageAt, "最近消息时间");
    }

    @Override
    public String id() {
        return sessionId;
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
