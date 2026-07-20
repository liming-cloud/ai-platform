package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ChatMessageHistoryResult;
import java.time.Instant;
import java.util.List;

public record ChatMessageHistoryResponse(
        String messageId,
        String sessionId,
        String applicationId,
        String role,
        String content,
        List<CitationResponse> citations,
        int citationCount,
        String modelInvocationLogId,
        String traceId,
        Instant createdAt
) {
    public static ChatMessageHistoryResponse from(ChatMessageHistoryResult result) {
        return new ChatMessageHistoryResponse(
                result.messageId(),
                result.sessionId(),
                result.applicationId(),
                result.role(),
                result.content(),
                result.citations().stream().map(CitationResponse::from).toList(),
                result.citationCount(),
                result.modelInvocationLogId(),
                result.traceId(),
                result.createdAt()
        );
    }
}
