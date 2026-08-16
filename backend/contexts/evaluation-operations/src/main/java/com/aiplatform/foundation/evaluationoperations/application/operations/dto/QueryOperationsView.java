package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

/** 查询受理、终态、运行中、Token 与比率投影。 */
public record QueryOperationsView(
        long accepted,
        long answered,
        long refused,
        long failed,
        long running,
        long totalTokens,
        OperationsRates rates,
        java.util.List<QueryDailyPointView> daily) {
}
