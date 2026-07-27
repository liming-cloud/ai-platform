package com.aiplatform.foundation.trustedquery.domain.query.policy;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.util.Objects;

/**
 * 定义查询接受事务需要执行的固定演示限额。
 *
 * @param dailyQueryLimit 每个租户在 UTC 自然日内最多接受的查询数
 * @param concurrentQueryLimit 每个租户最多持有的有效运行租约数
 * @param runningLease 运行占用的租约时长
 */
public record QueryQuotaPolicy(
        int dailyQueryLimit,
        int concurrentQueryLimit,
        Duration runningLease) implements ValueObject {

    public QueryQuotaPolicy {
        if (dailyQueryLimit < 1) {
            throw new IllegalArgumentException("每日查询限额必须大于零");
        }
        if (concurrentQueryLimit < 1) {
            throw new IllegalArgumentException("并发查询限额必须大于零");
        }
        Objects.requireNonNull(runningLease, "运行租约时长不能为空");
        if (runningLease.isZero() || runningLease.isNegative()) {
            throw new IllegalArgumentException("运行租约时长必须大于零");
        }
    }
}
