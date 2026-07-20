package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ChatSessionSummaryResult;
import java.time.Instant;

public record ChatSessionSummaryResponse(
        String sessionId,
        String applicationId,
        String releaseId,
        long releaseVersion,
        boolean requiresNewSession,
        String userId,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) {
    public static ChatSessionSummaryResponse from(ChatSessionSummaryResult result) {
        return new ChatSessionSummaryResponse(
                result.sessionId(),
                result.applicationId(),
                result.releaseId(),
                result.releaseVersion(),
                result.requiresNewSession(),
                result.userId(),
                result.title(),
                result.createdAt(),
                result.lastMessageAt()
        );
    }
}
