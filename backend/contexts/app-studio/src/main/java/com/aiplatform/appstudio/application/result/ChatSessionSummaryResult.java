package com.aiplatform.appstudio.application.result;

import java.time.Instant;

/**
 * 会话历史摘要结果。
 */
public record ChatSessionSummaryResult(
        String sessionId,
        String applicationId,
        String releaseId,
        long releaseVersion,
        boolean requiresNewSession,
        String userId,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) {
}
