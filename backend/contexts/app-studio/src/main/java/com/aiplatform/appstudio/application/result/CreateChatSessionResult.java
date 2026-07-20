package com.aiplatform.appstudio.application.result;

import java.time.Instant;

/**
 * 创建会话结果，返回会话主键与归属应用。
 */
public record CreateChatSessionResult(
        String sessionId,
        String applicationId,
        String releaseId,
        long releaseVersion,
        String userId,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) {
}
