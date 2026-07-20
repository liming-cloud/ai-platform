package com.aiplatform.appstudio.domain.port.debug.model;

import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.List;

/**
 * 调试运行详情，提供历史回放所需的完整事实。
 */
public record AppStudioDebugRunDetail(
        String assistantMessageId,
        String userMessageId,
        String applicationId,
        String sessionId,
        String traceId,
        String modelInvocationLogId,
        String invocationStatus,
        boolean answerable,
        String userMessageContent,
        String assistantMessageContent,
        String noAnswerReason,
        List<Citation> citations,
        List<AppStudioDebugRunEvidenceSnapshot> evidences,
        String providerCode,
        String modelCode,
        String decisionSource,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String errorCode,
        String errorMessage,
        Instant createdAt
) implements ValueObject {
    public AppStudioDebugRunDetail {
        assistantMessageId = AppStudioPortContracts.requireText(assistantMessageId, "助手消息 ID");
        userMessageId = AppStudioPortContracts.requireText(userMessageId, "用户消息 ID");
        applicationId = AppStudioPortContracts.requireText(applicationId, "应用 ID");
        sessionId = AppStudioPortContracts.requireText(sessionId, "会话 ID");
        traceId = AppStudioPortContracts.requireText(traceId, "Trace ID");
        modelInvocationLogId = normalizeOptionalText(modelInvocationLogId);
        invocationStatus = normalizeOptionalText(invocationStatus);
        userMessageContent = AppStudioPortContracts.requireText(userMessageContent, "用户消息内容");
        assistantMessageContent = AppStudioPortContracts.requireText(assistantMessageContent, "助手消息内容");
        noAnswerReason = normalizeOptionalText(noAnswerReason);
        citations = AppStudioPortContracts.copyRequiredList(citations, "引用列表");
        evidences = AppStudioPortContracts.copyRequiredList(evidences, "证据列表");
        providerCode = normalizeOptionalText(providerCode);
        modelCode = normalizeOptionalText(modelCode);
        decisionSource = normalizeOptionalText(decisionSource);
        sourceContext = normalizeOptionalText(sourceContext);
        sourceResourceType = normalizeOptionalText(sourceResourceType);
        sourceResourceId = normalizeOptionalText(sourceResourceId);
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
