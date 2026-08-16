package com.aiplatform.foundation.trustedquery.application.operations.dto;

import java.time.LocalDate;

/** 按 UTC 自然日聚合的查询趋势事实，不包含任何查询正文或成员信息。 */
public record QueryDailyOperationsFacts(
        LocalDate date,
        long accepted,
        long answered,
        long refused,
        long failed,
        long totalTokens) {
}
