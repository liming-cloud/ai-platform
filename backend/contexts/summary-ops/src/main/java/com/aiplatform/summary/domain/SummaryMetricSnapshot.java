package com.aiplatform.summary.domain;

import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SummaryMetricSnapshot(
        String tenantId,
        SummaryMetricScope scope,
        String metricCode,
        Map<String, Object> payload,
        SummaryMetricFreshness freshness,
        boolean partial,
        String failedReason,
        Instant asOf
) {
    public SummaryMetricSnapshot {
        tenantId = requireText(tenantId, "tenantId");
        Objects.requireNonNull(scope, "指标范围不能为空");
        metricCode = requireText(metricCode, "指标编码");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        Objects.requireNonNull(freshness, "指标新鲜度不能为空");
        failedReason = normalize(failedReason);
        Objects.requireNonNull(asOf, "指标时间不能为空");
    }

    public SummaryMetricSnapshot failed(String reason) {
        return new SummaryMetricSnapshot(
                tenantId,
                scope,
                metricCode,
                payload,
                freshness,
                true,
                requireText(reason, "失败原因"),
                asOf
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
