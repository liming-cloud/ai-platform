package com.aiplatform.foundation.evaluationoperations.api.model;

import java.time.Instant;

/**
 * 当前租户的查询状态、令牌和配额汇总。
 */
public record QueryUsageView(
        Instant from,
        Instant to,
        long acceptedQueries,
        long answeredQueries,
        long refusedQueries,
        long failedQueries,
        long runningQueries,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        DailyQueryQuotaView dailyQuota,
        ConcurrentQueryQuotaView concurrentQuota,
        Instant generatedAt) {
}
