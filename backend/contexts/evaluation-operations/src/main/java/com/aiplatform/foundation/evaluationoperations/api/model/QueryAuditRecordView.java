package com.aiplatform.foundation.evaluationoperations.api.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 不包含问题正文、回答和引用内容的查询审计视图。
 */
public record QueryAuditRecordView(
        UUID queryRunId,
        UUID requestedBy,
        UUID assistantVersionId,
        String questionDigest,
        String status,
        String refusalReason,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdAt,
        Instant completedAt) {
}
