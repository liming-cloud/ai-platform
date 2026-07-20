package com.aiplatform.contracts.capability.summary;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SummaryMetricValue(
        String metricCode,
        Map<String, Object> payload,
        SummaryMetricFreshness freshness,
        Instant asOf
) {
    public SummaryMetricValue {
        if (metricCode == null || metricCode.isBlank()) {
            throw new IllegalArgumentException("metricCode must not be blank");
        }
        metricCode = metricCode.trim();
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        Objects.requireNonNull(freshness, "summary metric freshness must not be null");
        Objects.requireNonNull(asOf, "summary metric asOf must not be null");
    }
}
