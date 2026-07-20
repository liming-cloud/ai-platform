package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSummary;
import java.time.Instant;

public record AppStudioDebugRunSummaryResponse(
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
) {
    public static AppStudioDebugRunSummaryResponse from(AppStudioDebugRunSummary summary) {
        return new AppStudioDebugRunSummaryResponse(
                summary.assistantMessageId(),
                summary.userMessageId(),
                summary.applicationId(),
                summary.sessionId(),
                summary.traceId(),
                summary.modelInvocationLogId(),
                summary.invocationStatus(),
                summary.answerable(),
                summary.citationCount(),
                summary.evidenceCount(),
                summary.providerCode(),
                summary.modelCode(),
                summary.inputTokens(),
                summary.outputTokens(),
                summary.latencyMs(),
                summary.createdAt()
        );
    }
}
