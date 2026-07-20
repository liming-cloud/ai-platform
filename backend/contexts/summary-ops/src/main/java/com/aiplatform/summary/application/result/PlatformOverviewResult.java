package com.aiplatform.summary.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PlatformOverviewResult(
        Instant asOf,
        String freshness,
        boolean partial,
        List<String> failedMetrics,
        String traceId,
        Map<String, Object> tenantHealth,
        Map<String, Object> modelHealth,
        Map<String, Object> approvalTodos,
        Map<String, Object> ragSecurity,
        Map<String, Object> appPublishBlocks
) {
}
