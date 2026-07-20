package com.aiplatform.summary.application;

import com.aiplatform.summary.domain.SummaryMetricSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;

record SummaryMetricAggregation(
        Instant asOf,
        String freshness,
        boolean partial,
        List<String> failedMetrics,
        String traceId,
        Map<String, SummaryMetricSnapshot> values
) {
    Map<String, Object> payload(String metricCode) {
        SummaryMetricSnapshot snapshot = values.get(metricCode);
        return snapshot == null ? Map.of() : snapshot.payload();
    }
}
