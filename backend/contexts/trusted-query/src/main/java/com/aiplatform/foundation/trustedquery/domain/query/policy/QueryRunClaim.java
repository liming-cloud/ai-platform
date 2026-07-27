package com.aiplatform.foundation.trustedquery.domain.query.policy;

import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import java.util.Objects;

/**
 * 查询运行占位结果。
 */
public record QueryRunClaim(QueryRun queryRun, QueryRunClaimState state) {

    public QueryRunClaim {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
        Objects.requireNonNull(state, "占位状态不能为空");
    }
}
