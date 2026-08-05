package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 评测数据集聚合根的稳定标识。
 */
public record EvaluationDatasetId(long value) implements ValueObject {

    public EvaluationDatasetId {
        if (value <= 0L) {
            throw new IllegalArgumentException("评测数据集标识必须是正整数");
        }
    }
}
