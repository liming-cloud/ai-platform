package com.aiplatform.foundation.trustedquery.application.operations.query;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 定义统计范围和当前配额观察点的跨上下文查询。
 */
public record QueryUsageFactQuery(
        TenantId tenantId,
        Instant from,
        Instant to,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant observedAt) {

    public QueryUsageFactQuery {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(from, "统计开始时间不能为空");
        Objects.requireNonNull(to, "统计结束时间不能为空");
        Objects.requireNonNull(currentPeriodStart, "当前配额周期开始时间不能为空");
        Objects.requireNonNull(currentPeriodEnd, "当前配额周期结束时间不能为空");
        Objects.requireNonNull(observedAt, "用量观察时间不能为空");
        if (!to.isAfter(from)
                || !currentPeriodEnd.isAfter(currentPeriodStart)
                || observedAt.isBefore(currentPeriodStart)
                || !observedAt.isBefore(currentPeriodEnd)) {
            throw new IllegalArgumentException("用量事实查询时间范围不正确");
        }
    }
}
