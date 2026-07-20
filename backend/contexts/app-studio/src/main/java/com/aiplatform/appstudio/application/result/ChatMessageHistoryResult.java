package com.aiplatform.appstudio.application.result;

import java.time.Instant;
import java.util.List;

/**
 * 聊天消息历史结果。
 */
public record ChatMessageHistoryResult(
        String messageId,
        String sessionId,
        String applicationId,
        String role,
        String content,
        List<CitationResult> citations,
        int citationCount,
        String modelInvocationLogId,
        String traceId,
        Instant createdAt
) {
}
