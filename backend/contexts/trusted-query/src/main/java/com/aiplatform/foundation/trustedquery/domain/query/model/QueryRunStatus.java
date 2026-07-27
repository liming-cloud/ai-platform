package com.aiplatform.foundation.trustedquery.domain.query.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 一次可信查询运行的持久化状态。
 */
public enum QueryRunStatus implements ValueObject {
    RUNNING,
    ANSWERED,
    REFUSED,
    FAILED
}
