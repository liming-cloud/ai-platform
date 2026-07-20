package com.aiplatform.knowledge.domain.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RAG 产品化质量门禁，供评测中心和发布流水线复用。
 */
public record RagQualityGate(
        double minRecallAt10,
        double minPrecisionAt5,
        double minMrrAt10,
        double minCitationAccuracy,
        double minNoAnswerAccuracy,
        double minGroundedness,
        long maxRetrievalP95Ms,
        long maxAnswerP95Ms,
        double maxMetricDropRate
) {
    public static RagQualityGate p1Default() {
        return new RagQualityGate(0.85, 0.70, 0.70, 0.90, 0.95, 0.85, 1_000, 8_000, 0.03);
    }

    public RagQualityGateDecision evaluate(RagQualityMetrics metrics) {
        Objects.requireNonNull(metrics, "RAG 质量指标不能为空");
        List<String> findings = new ArrayList<>();
        if (metrics.permissionLeakCount() != 0) {
            findings.add("权限泄漏数量必须为 0");
        }
        if (metrics.crossTenantRecallCount() != 0) {
            findings.add("跨租户召回数量必须为 0");
        }
        addFindingIfBelow(findings, metrics.recallAt10(), minRecallAt10, "Recall@10 低于 %.2f");
        addFindingIfBelow(findings, metrics.precisionAt5(), minPrecisionAt5, "Precision@5 低于 %.2f");
        addFindingIfBelow(findings, metrics.mrrAt10(), minMrrAt10, "MRR@10 低于 %.2f");
        addFindingIfBelow(findings, metrics.citationAccuracy(), minCitationAccuracy, "引用准确率低于 %.2f");
        addFindingIfBelow(findings, metrics.noAnswerAccuracy(), minNoAnswerAccuracy, "无答案准确率低于 %.2f");
        addFindingIfBelow(findings, metrics.groundedness(), minGroundedness, "Groundedness 低于 %.2f");
        if (metrics.retrievalP95Ms() > maxRetrievalP95Ms) {
            findings.add("检索 p95 超过 " + maxRetrievalP95Ms + "ms");
        }
        if (metrics.answerP95Ms() > maxAnswerP95Ms) {
            findings.add("完整问答 p95 超过 " + maxAnswerP95Ms + "ms");
        }
        if (metrics.metricDropRate() > maxMetricDropRate) {
            findings.add("关键指标下降超过 3%");
        }
        return new RagQualityGateDecision(!findings.isEmpty(), findings);
    }

    private static void addFindingIfBelow(List<String> findings, double actual, double expected, String message) {
        if (actual < expected) {
            findings.add(message.formatted(expected));
        }
    }
}
