package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 单次评测运行聚合根的稳定标识。
 */
public record EvaluationRunId(long value) implements ValueObject {

    public EvaluationRunId {
        if (value <= 0L) {
            throw new IllegalArgumentException("评测运行标识必须是正整数");
        }
    }
}
