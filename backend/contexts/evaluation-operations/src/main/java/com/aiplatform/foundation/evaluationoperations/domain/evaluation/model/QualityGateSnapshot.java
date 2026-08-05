package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/**
 * 随评测运行冻结的质量门禁阈值，避免以后续策略重算历史结论。
 */
public record QualityGateSnapshot(
        String policyVersion,
        double minimumRecallAt10,
        double minimumCitationCorrectness,
        double minimumCorrectRefusalRate) implements ValueObject {

    public QualityGateSnapshot {
        if (policyVersion == null || policyVersion.isBlank() || !policyVersion.equals(policyVersion.trim())) {
            throw new IllegalArgumentException("质量门禁策略版本不正确");
        }
        requireRate(minimumRecallAt10, "检索召回阈值");
        requireRate(minimumCitationCorrectness, "引用正确率阈值");
        requireRate(minimumCorrectRefusalRate, "正确拒答率阈值");
    }

    private static void requireRate(double value, String label) {
        if (!Double.isFinite(value) || value <= 0D || value > 1D) {
            throw new IllegalArgumentException(Objects.requireNonNull(label) + "不正确");
        }
    }
}
