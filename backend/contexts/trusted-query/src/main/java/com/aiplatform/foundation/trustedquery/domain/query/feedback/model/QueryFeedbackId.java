package com.aiplatform.foundation.trustedquery.domain.query.feedback.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 查询反馈的稳定标识。
 */
public record QueryFeedbackId(long value) implements ValueObject {

    public QueryFeedbackId {
        if (value <= 0) {
            throw new IllegalArgumentException("查询反馈标识必须是正整数");
        }
    }
}
