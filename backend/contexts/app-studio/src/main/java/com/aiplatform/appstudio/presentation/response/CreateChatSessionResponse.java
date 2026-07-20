package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.CreateChatSessionResult;
import java.time.Instant;

public record CreateChatSessionResponse(
        String sessionId,
        String applicationId,
        String releaseId,
        long releaseVersion,
        String userId,
        String title,
        Instant createdAt,
        Instant lastMessageAt
) {
    public static CreateChatSessionResponse from(CreateChatSessionResult result) {
        return new CreateChatSessionResponse(
                result.sessionId(),
                result.applicationId(),
                result.releaseId(),
                result.releaseVersion(),
                result.userId(),
                result.title(),
                result.createdAt(),
                result.lastMessageAt()
        );
    }
}
