package com.aiplatform.foundation.trustedquery.domain;

/**
 * 查询运行对外可重放的稳定事件类型。
 */
public enum QueryRunEventType {
    QUERY_STARTED,
    QUERY_ANSWERED,
    QUERY_REFUSED,
    QUERY_FAILED
}
