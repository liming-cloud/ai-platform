package com.aiplatform.knowledge.domain.retrieval;

/**
 * RAG 质量评测指标快照，用于发布前质量门禁判断。
 */
public record RagQualityMetrics(
        double recallAt10,
        double precisionAt5,
        double mrrAt10,
        double citationAccuracy,
        double noAnswerAccuracy,
        double groundedness,
        long retrievalP95Ms,
        long answerP95Ms,
        int permissionLeakCount,
        int crossTenantRecallCount,
        double metricDropRate
) {
}
