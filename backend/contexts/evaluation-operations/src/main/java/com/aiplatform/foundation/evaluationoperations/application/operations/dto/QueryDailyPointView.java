package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

import java.time.LocalDate;

/** 查询量趋势的 UTC 自然日脱敏点。 */
public record QueryDailyPointView(
        LocalDate date,
        long accepted,
        long answered,
        long refused,
        long failed,
        long totalTokens) {
}
