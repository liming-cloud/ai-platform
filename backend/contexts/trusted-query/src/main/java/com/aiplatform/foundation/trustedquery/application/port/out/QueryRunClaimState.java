package com.aiplatform.foundation.trustedquery.application.port.out;

/**
 * 幂等查询占位的结果。
 */
public enum QueryRunClaimState {
    CLAIMED,
    REPLAYED,
    IN_PROGRESS
}
