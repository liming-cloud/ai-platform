package com.aiplatform.appstudio.domain.port.chat.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;

/**
 * 模型调用审计记录，避免 App Studio 消息与真实模型调用日志脱节。
 */
public record ModelInvocationAuditRecord(
        TenantId tenantId,
        String traceId,
        String idempotencyKey,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String modelConfigId,
        String requestHash,
        int inputTokens,
        int outputTokens,
        int latencyMs,
        String status,
        String errorCode,
        String errorMessage,
        Instant createdAt
) implements ValueObject {
    public ModelInvocationAuditRecord(
            TenantId tenantId,
            String traceId,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String modelConfigId,
            String requestHash,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            String status,
            String errorCode,
            String errorMessage,
            Instant createdAt
    ) {
        this(tenantId, traceId, traceId, sourceContext, sourceResourceType, sourceResourceId, modelConfigId,
                requestHash, inputTokens, outputTokens, latencyMs, status, errorCode, errorMessage, createdAt);
    }

    public ModelInvocationAuditRecord {
        tenantId = AppStudioPortContracts.requireValue(tenantId, "租户 ID");
        traceId = AppStudioPortContracts.requireText(traceId, "调用追踪 ID");
        idempotencyKey = AppStudioPortContracts.requireText(idempotencyKey, "调用幂等键");
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("调用幂等键长度不能超过 128");
        }
        sourceContext = AppStudioPortContracts.requireText(sourceContext, "调用来源上下文");
        sourceResourceType = normalizeOptionalText(sourceResourceType);
        sourceResourceId = normalizeOptionalText(sourceResourceId);
        modelConfigId = AppStudioPortContracts.requireText(modelConfigId, "模型配置 ID");
        requestHash = normalizeOptionalText(requestHash);
        if (inputTokens < 0 || outputTokens < 0 || latencyMs < 0) {
            throw new IllegalArgumentException("模型调用审计数值不能小于 0");
        }
        status = AppStudioPortContracts.requireText(status, "调用状态");
        errorCode = normalizeOptionalText(errorCode);
        errorMessage = normalizeOptionalText(errorMessage);
        createdAt = AppStudioPortContracts.requireValue(createdAt, "创建时间");
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
