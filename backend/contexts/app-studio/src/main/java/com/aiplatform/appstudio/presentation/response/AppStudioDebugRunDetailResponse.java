package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.DebugEvidenceResult;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import java.time.Instant;
import java.util.List;

public record AppStudioDebugRunDetailResponse(
        boolean masked,
        String auditEventId,
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
        List<CitationResponse> citations,
        List<RetrievalEvidenceResponse> evidences,
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
) {
    public static AppStudioDebugRunDetailResponse from(DebugEvidenceResult result) {
        AppStudioDebugRunDetail detail = result.detail();
        boolean masked = result.masked();
        return new AppStudioDebugRunDetailResponse(
                masked,
                result.auditEventId(),
                detail.assistantMessageId(),
                detail.userMessageId(),
                detail.applicationId(),
                detail.sessionId(),
                detail.traceId(),
                detail.modelInvocationLogId(),
                detail.invocationStatus(),
                detail.answerable(),
                masked ? null : detail.userMessageContent(),
                masked ? null : detail.assistantMessageContent(),
                masked ? null : detail.noAnswerReason(),
                masked ? List.of() : detail.citations().stream().map(CitationResponse::from).toList(),
                masked ? List.of() : detail.evidences().stream().map(RetrievalEvidenceResponse::from).toList(),
                masked ? null : detail.providerCode(),
                masked ? null : detail.modelCode(),
                detail.decisionSource(),
                detail.inputTokens(),
                detail.outputTokens(),
                detail.latencyMs(),
                masked ? null : detail.sourceContext(),
                masked ? null : detail.sourceResourceType(),
                masked ? null : detail.sourceResourceId(),
                masked ? null : detail.errorCode(),
                masked ? null : detail.errorMessage(),
                detail.createdAt()
        );
    }
}
