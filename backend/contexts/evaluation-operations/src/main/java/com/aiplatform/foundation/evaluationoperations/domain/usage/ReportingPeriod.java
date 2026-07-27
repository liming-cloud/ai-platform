package com.aiplatform.foundation.evaluationoperations.domain.usage;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 运营报表的半开时间区间，统一限制为最多三十一天。
 */
public record ReportingPeriod(Instant from, Instant to) {

    private static final Duration MAXIMUM_RANGE = Duration.ofDays(31);

    public ReportingPeriod {
        Objects.requireNonNull(from, "统计开始时间不能为空");
        Objects.requireNonNull(to, "统计结束时间不能为空");
        var duration = Duration.between(from, to);
        if (duration.isZero()
                || duration.isNegative()
                || duration.compareTo(MAXIMUM_RANGE) > 0) {
            throw new IllegalArgumentException(
                    "统计时间范围必须大于零且不超过三十一天");
        }
    }

    public static ReportingPeriod requestedOrCurrentDay(
            Instant requestedFrom,
            Instant requestedTo,
            Instant now) {
        Objects.requireNonNull(now, "当前时间不能为空");
        if (requestedFrom == null && requestedTo == null) {
            return utcDayContaining(now);
        }
        if (requestedFrom == null || requestedTo == null) {
            throw new IllegalArgumentException("统计开始和结束时间必须同时提供");
        }
        return new ReportingPeriod(requestedFrom, requestedTo);
    }

    public static ReportingPeriod utcDayContaining(Instant value) {
        Objects.requireNonNull(value, "时间不能为空");
        var from = value.atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        return new ReportingPeriod(from, from.plus(Duration.ofDays(1)));
    }
}
