package com.aiplatform.foundation.trustedquery.domain;

/**
 * 一次可信查询运行的持久化状态。
 */
public enum QueryRunStatus {
    RUNNING,
    ANSWERED,
    REFUSED,
    FAILED
}
