package com.aiplatform.foundation.trustedquery.domain.query.policy;

/**
 * 幂等查询占位的结果。
 */
public enum QueryRunClaimState {
    CLAIMED,
    REPLAYED,
    IN_PROGRESS
}
