package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ChatMessageFeedbackResult;
import java.time.Instant;

public record ChatMessageFeedbackResponse(String feedbackId, String status, String rating, Instant updatedAt) {
    public static ChatMessageFeedbackResponse from(ChatMessageFeedbackResult result) {
        return new ChatMessageFeedbackResponse(result.feedbackId(), result.status(), result.rating(), result.updatedAt());
    }
}
