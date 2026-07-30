package com.aiplatform.foundation.trustedquery.domain.query.feedback.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 请求成员对一次可信查询结果作出的正负判断。
 */
public enum QueryFeedbackOutcome implements ValueObject {
    POSITIVE,
    NEGATIVE
}
