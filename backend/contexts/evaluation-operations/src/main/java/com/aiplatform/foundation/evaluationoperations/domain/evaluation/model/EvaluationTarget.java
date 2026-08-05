package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 被评测草稿助手版本的冻结标识与修订。
 */
public record EvaluationTarget(long assistantVersionId, long assistantVersionRevision) implements ValueObject {

    public EvaluationTarget {
        if (assistantVersionId <= 0L || assistantVersionRevision < 0L) {
            throw new IllegalArgumentException("评测目标版本标识或修订不正确");
        }
    }
}
