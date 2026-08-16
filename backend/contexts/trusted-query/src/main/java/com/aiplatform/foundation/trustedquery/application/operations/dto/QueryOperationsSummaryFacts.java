package com.aiplatform.foundation.trustedquery.application.operations.dto;

/** 运营总览可消费的查询状态与 Token 脱敏计数。 */
public record QueryOperationsSummaryFacts(
        long acceptedQueries,
        long answeredQueries,
        long refusedQueries,
        long failedQueries,
        long runningQueries,
        long totalTokens,
        java.util.List<QueryDailyOperationsFacts> daily) {

    public QueryOperationsSummaryFacts(
            long acceptedQueries, long answeredQueries, long refusedQueries,
            long failedQueries, long runningQueries, long totalTokens) {
        this(acceptedQueries, answeredQueries, refusedQueries, failedQueries, runningQueries,
                totalTokens, java.util.List.of());
    }
}
