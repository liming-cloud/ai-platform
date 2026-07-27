package com.aiplatform.foundation.trustedquery.application.quota;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.trustedquery.domain.common.error.TrustedQueryErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 表示查询在产生检索或模型副作用前被租户配额拒绝。
 */
public final class QueryQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    private final QueryQuotaLimitType limitType;
    private final int limit;
    private final Instant retryAt;

    public QueryQuotaExceededException(
            QueryQuotaLimitType limitType,
            int limit,
            Instant retryAt) {
        super(
                TrustedQueryErrorCode.QUOTA,
                Map.of(
                        "limitType", Objects.requireNonNull(
                                limitType,
                                "配额类型不能为空").name(),
                        "limit", limit,
                        "retryAt", Objects.requireNonNull(
                                retryAt,
                                "重试时间不能为空").toString()));
        this.limitType = limitType;
        if (limit < 1) {
            throw new IllegalArgumentException("配额上限必须大于零");
        }
        this.limit = limit;
        this.retryAt = retryAt;
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
