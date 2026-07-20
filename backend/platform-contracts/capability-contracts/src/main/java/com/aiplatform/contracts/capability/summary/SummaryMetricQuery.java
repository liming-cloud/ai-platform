package com.aiplatform.contracts.capability.summary;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record SummaryMetricQuery(
        SummaryMetricScope scope,
        String tenantId,
        Instant asOf,
        Map<String, String> filters
) {
    public SummaryMetricQuery {
        Objects.requireNonNull(scope, "summary metric scope must not be null");
        tenantId = requireText(tenantId, "tenantId");
        Objects.requireNonNull(asOf, "summary metric asOf must not be null");
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        if (scope == SummaryMetricScope.PLATFORM && !"0".equals(tenantId)) {
            throw new IllegalArgumentException("platform summary metric tenantId must be 0");
        }
        if (scope == SummaryMetricScope.TENANT && "0".equals(tenantId)) {
            throw new IllegalArgumentException("tenant summary metric tenantId must not be 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
