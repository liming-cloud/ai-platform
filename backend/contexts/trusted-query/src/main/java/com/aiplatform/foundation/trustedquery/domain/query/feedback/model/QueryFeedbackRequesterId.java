package com.aiplatform.foundation.trustedquery.domain.query.feedback.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 提交查询反馈的请求成员标识。
 */
public record QueryFeedbackRequesterId(long value) implements ValueObject {

    public QueryFeedbackRequesterId {
        if (value <= 0) {
            throw new IllegalArgumentException("查询反馈请求成员标识必须是正整数");
        }
    }
}
