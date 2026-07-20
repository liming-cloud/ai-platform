package com.aiplatform.summary.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TenantWorkbenchResult(
        String tenantId,
        Instant asOf,
        String freshness,
        boolean partial,
        List<String> failedMetrics,
        String traceId,
        List<String> enabledFeatures,
        Map<String, Object> userRoleGaps,
        Map<String, Object> knowledgeHealth,
        Map<String, Object> appHealth,
        Map<String, Object> permissionTodos
) {
}
