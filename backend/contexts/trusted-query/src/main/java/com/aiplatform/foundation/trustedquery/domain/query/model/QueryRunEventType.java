package com.aiplatform.foundation.trustedquery.domain.query.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 查询运行对外可重放的稳定事件类型。
 */
public enum QueryRunEventType implements ValueObject {
    QUERY_STARTED,
    QUERY_ANSWERED,
    QUERY_REFUSED,
    QUERY_FAILED
}
