package com.aiplatform.foundation.trustedquery.domain.query.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 冻结一次查询被接受时的配额周期、占用序号和运行租约。
 *
 * @param periodStart UTC 自然日开始时间
 * @param periodEnd UTC 自然日结束时间
 * @param dailyLimit 接受时的每日查询上限
 * @param dailyOrdinal 本查询在周期内占用的序号
 * @param concurrentLimit 接受时的并发运行上限
 * @param acceptedLeaseUntil 接受时确定的运行租约截止时间
 */
public record QueryQuotaSnapshot(
        Instant periodStart,
        Instant periodEnd,
        int dailyLimit,
        int dailyOrdinal,
        int concurrentLimit,
        Instant acceptedLeaseUntil) implements ValueObject {

    public QueryQuotaSnapshot {
        Objects.requireNonNull(periodStart, "配额周期开始时间不能为空");
        Objects.requireNonNull(periodEnd, "配额周期结束时间不能为空");
        Objects.requireNonNull(acceptedLeaseUntil, "运行租约截止时间不能为空");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("配额周期结束时间必须晚于开始时间");
        }
        if (dailyLimit < 1 || dailyOrdinal < 1 || dailyOrdinal > dailyLimit) {
            throw new IllegalArgumentException("每日配额占用事实不正确");
        }
        if (concurrentLimit < 1) {
            throw new IllegalArgumentException("并发查询限额必须大于零");
        }
    }

    public static QueryQuotaSnapshot forAcceptedQuery(
            Instant acceptedAt,
            int dailyLimit,
            int dailyOrdinal,
            int concurrentLimit,
            Duration runningLease) {
        Objects.requireNonNull(acceptedAt, "查询接受时间不能为空");
        Objects.requireNonNull(runningLease, "运行租约时长不能为空");
        if (runningLease.isZero() || runningLease.isNegative()) {
            throw new IllegalArgumentException("运行租约时长必须大于零");
        }
        var periodStart = acceptedAt
                .atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        return new QueryQuotaSnapshot(
                periodStart,
                periodStart.plus(Duration.ofDays(1)),
                dailyLimit,
                dailyOrdinal,
                concurrentLimit,
                acceptedAt.plus(runningLease));
    }
}
