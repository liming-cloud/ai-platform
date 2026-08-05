package com.aiplatform.foundation.evaluationoperations.domain.evaluation.policy;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.QualityGateSnapshot;
import com.aiplatform.foundation.shared.kernel.domain.DomainPolicy;

/**
 * `quality-gate-v1` 的无状态策略入口，只生成可随运行冻结的阈值快照。
 */
public final class QualityGatePolicy implements DomainPolicy {

    private static final QualityGatePolicy QUALITY_GATE_V1 = new QualityGatePolicy();

    private QualityGatePolicy() {
    }

    public static QualityGatePolicy qualityGateV1() {
        return QUALITY_GATE_V1;
    }

    public QualityGateSnapshot snapshot() {
        return new QualityGateSnapshot("quality-gate-v1", 0.85D, 0.95D, 0.98D);
    }
}
