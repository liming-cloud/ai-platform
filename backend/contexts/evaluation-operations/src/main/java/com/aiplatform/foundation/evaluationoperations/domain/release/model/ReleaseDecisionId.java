package com.aiplatform.foundation.evaluationoperations.domain.release.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 发布决定聚合根的稳定标识。
 */
public record ReleaseDecisionId(long value) implements ValueObject {

    public ReleaseDecisionId {
        if (value <= 0L) {
            throw new IllegalArgumentException("发布决定标识必须是正整数");
        }
    }
}
