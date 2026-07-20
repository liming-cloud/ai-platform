package com.aiplatform.appstudio.domain.port.debug.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;

/**
 * 调试运行列表项。
 */
public record AppStudioDebugRunSummary(
        String assistantMessageId,
        String userMessageId,
        String applicationId,
        String sessionId,
        String traceId,
        String modelInvocationLogId,
        String invocationStatus,
        boolean answerable,
        int citationCount,
        int evidenceCount,
        String providerCode,
        String modelCode,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        Instant createdAt
) implements ValueObject {
    public AppStudioDebugRunSummary {
        assistantMessageId = AppStudioPortContracts.requireText(assistantMessageId, "助手消息 ID");
        userMessageId = AppStudioPortContracts.requireText(userMessageId, "用户消息 ID");
        applicationId = AppStudioPortContracts.requireText(applicationId, "应用 ID");
        sessionId = AppStudioPortContracts.requireText(sessionId, "会话 ID");
        traceId = AppStudioPortContracts.requireText(traceId, "Trace ID");
        modelInvocationLogId = normalizeOptionalText(modelInvocationLogId);
        invocationStatus = normalizeOptionalText(invocationStatus);
        if (citationCount < 0 || evidenceCount < 0) {
            throw new IllegalArgumentException("引用数和证据数不能小于 0");
        }
        providerCode = normalizeOptionalText(providerCode);
        modelCode = normalizeOptionalText(modelCode);
        createdAt = AppStudioPortContracts.requireValue(createdAt, "创建时间");
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
