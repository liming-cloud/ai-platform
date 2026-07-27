package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

import java.time.Instant;

/**
 * 不包含问题正文、回答和引用内容的查询审计视图。
 */
public record QueryAuditRecordView(
        String queryRunId,
        String requestedBy,
        String assistantVersionId,
        String questionDigest,
        String status,
        String refusalReason,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdAt,
        Instant completedAt) {
}
