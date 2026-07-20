package com.aiplatform.appstudio.domain.port.debug.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;

/**
 * 调试运行查询过滤条件。
 */
public record AppStudioDebugRunQueryFilter(
        TenantId tenantId,
        String applicationId,
        String userId,
        String sessionId,
        String traceId,
        String modelInvocationLogId,
        String invocationStatus,
        int limit
) implements ValueObject {
    public AppStudioDebugRunQueryFilter {
        tenantId = AppStudioPortContracts.requireValue(tenantId, "租户 ID");
        applicationId = normalizeOptionalText(applicationId);
        sessionId = normalizeOptionalText(sessionId);
        traceId = normalizeOptionalText(traceId);
        modelInvocationLogId = normalizeOptionalText(modelInvocationLogId);
        invocationStatus = normalizeOptionalText(invocationStatus);
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("查询条数必须在 1 到 100 之间");
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
