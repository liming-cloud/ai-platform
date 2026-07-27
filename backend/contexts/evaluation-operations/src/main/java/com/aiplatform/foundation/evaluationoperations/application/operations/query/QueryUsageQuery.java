package com.aiplatform.foundation.evaluationoperations.application.operations.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Instant;
import java.util.Objects;

/**
 * 请求当前租户在给定时间范围内的查询用量。
 */
public record QueryUsageQuery(
        TrustedRequestContext context,
        Instant from,
        Instant to) {

    public QueryUsageQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
    }
}
