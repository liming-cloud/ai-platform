package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

/** 当前 UTC 自然日和当前并发的查询配额快照。 */
public record OperationsQuotaView(
        long dailyUsed,
        int dailyLimit,
        long concurrentUsed,
        int concurrentLimit) {
}
