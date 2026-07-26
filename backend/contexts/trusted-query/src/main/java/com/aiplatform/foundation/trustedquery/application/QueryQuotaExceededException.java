package com.aiplatform.foundation.trustedquery.application;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示查询在产生检索或模型副作用前被租户配额拒绝。
 */
public final class QueryQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final QueryQuotaLimitType limitType;
    private final int limit;
    private final Instant retryAt;

    public QueryQuotaExceededException(
            QueryQuotaLimitType limitType,
            int limit,
            Instant retryAt) {
        super("查询配额已用尽");
        this.limitType = Objects.requireNonNull(limitType, "配额类型不能为空");
        if (limit < 1) {
            throw new IllegalArgumentException("配额上限必须大于零");
        }
        this.limit = limit;
        this.retryAt = Objects.requireNonNull(retryAt, "重试时间不能为空");
    }

    public QueryQuotaLimitType limitType() {
        return limitType;
    }

    public int limit() {
        return limit;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
