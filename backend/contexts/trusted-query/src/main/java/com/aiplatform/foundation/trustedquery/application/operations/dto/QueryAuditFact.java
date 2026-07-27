package com.aiplatform.foundation.trustedquery.application.operations.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 可信查询上下文发布的单条脱敏审计事实。
 */
public record QueryAuditFact(
        String queryRunId,
        long requestedBy,
        String assistantVersionId,
        String questionDigest,
        String status,
        String refusalReason,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdAt,
        Instant completedAt) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public QueryAuditFact {
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        Objects.requireNonNull(requestedBy, "请求成员标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(status, "查询状态不能为空");
        Objects.requireNonNull(createdAt, "查询接受时间不能为空");
        if (questionDigest == null
                || !SHA256.matcher(questionDigest).matches()
                || inputTokens < 0
                || outputTokens < 0
                || totalTokens < 0) {
            throw new IllegalArgumentException("查询审计事实不正确");
        }
    }
}
