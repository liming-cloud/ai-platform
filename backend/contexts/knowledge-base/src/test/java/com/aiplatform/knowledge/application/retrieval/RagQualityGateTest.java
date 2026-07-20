package com.aiplatform.knowledge.domain.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagQualityGateTest {
    @Test
    void passesOnlyWhenEnterpriseRagMetricsReachDefaultGate() {
        RagQualityGate gate = RagQualityGate.p1Default();

        RagQualityGateDecision decision = gate.evaluate(new RagQualityMetrics(
                0.86,
                0.71,
                0.72,
                0.91,
                0.96,
                0.86,
                900,
                7_500,
                0,
                0,
                0.02
        ));

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.findings()).isEmpty();
    }

    @Test
    void blocksPermissionLeakAndRecallRegressionBeforeProductAcceptance() {
        RagQualityGate gate = RagQualityGate.p1Default();

        RagQualityGateDecision decision = gate.evaluate(new RagQualityMetrics(
                0.84,
                0.69,
                0.72,
                0.91,
                0.94,
                0.84,
                1_200,
                8_100,
                1,
                1,
                0.04
        ));

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.findings()).contains(
                "权限泄漏数量必须为 0",
                "跨租户召回数量必须为 0",
                "Recall@10 低于 0.85",
                "Precision@5 低于 0.70",
                "无答案准确率低于 0.95",
                "Groundedness 低于 0.85",
                "检索 p95 超过 1000ms",
                "完整问答 p95 超过 8000ms",
                "关键指标下降超过 3%"
        );
    }
}
