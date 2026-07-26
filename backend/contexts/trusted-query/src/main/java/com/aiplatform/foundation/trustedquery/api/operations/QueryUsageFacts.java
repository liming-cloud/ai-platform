package com.aiplatform.foundation.trustedquery.api.operations;

/**
 * 可信查询上下文发布的租户级脱敏计量事实。
 */
public record QueryUsageFacts(
        long acceptedQueries,
        long answeredQueries,
        long refusedQueries,
        long failedQueries,
        long runningQueries,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long dailyUsed,
        long concurrentUsed) {

    public QueryUsageFacts {
        if (acceptedQueries < 0
                || answeredQueries < 0
                || refusedQueries < 0
                || failedQueries < 0
                || runningQueries < 0
                || inputTokens < 0
                || outputTokens < 0
                || totalTokens < 0
                || dailyUsed < 0
                || concurrentUsed < 0
                || acceptedQueries
                        != answeredQueries
                                + refusedQueries
                                + failedQueries
                                + runningQueries) {
            throw new IllegalArgumentException("查询用量事实不正确");
        }
    }
}
