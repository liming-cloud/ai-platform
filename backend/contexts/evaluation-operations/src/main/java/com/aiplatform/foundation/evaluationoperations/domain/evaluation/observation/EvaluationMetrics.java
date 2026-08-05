package com.aiplatform.foundation.evaluationoperations.domain.evaluation.observation;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 冻结运行的三项质量指标及其是否可计算事实。
 */
public record EvaluationMetrics(
        boolean computable,
        double recallAt10,
        double citationCorrectness,
        double correctRefusalRate) implements ValueObject {

    /**
     * 仅由最小计数计算指标；任一分母为零时保持不可计算而非默认通过。
     */
    public static EvaluationMetrics from(EvaluationObservation observation) {
        var computable = observation.expectedEvidenceCount() > 0
                && observation.citationCount() > 0
                && observation.expectedRefusalCount() > 0;
        if (!computable) {
            return new EvaluationMetrics(false, 0D, 0D, 0D);
        }
        return new EvaluationMetrics(
                true,
                ratio(observation.retrievedExpectedEvidenceCount(), observation.expectedEvidenceCount()),
                ratio(observation.correctCitationCount(), observation.citationCount()),
                ratio(observation.correctRefusalCount(), observation.expectedRefusalCount()));
    }

    private static double ratio(int numerator, int denominator) {
        return (double) numerator / denominator;
    }
}
