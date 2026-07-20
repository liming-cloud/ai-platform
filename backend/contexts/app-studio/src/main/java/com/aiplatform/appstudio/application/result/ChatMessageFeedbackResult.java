package com.aiplatform.appstudio.application.result;

import com.aiplatform.appstudio.domain.ChatMessageFeedback;
import java.time.Instant;

public record ChatMessageFeedbackResult(String feedbackId, String status, String rating, Instant updatedAt) {
    public static ChatMessageFeedbackResult from(ChatMessageFeedback feedback) {
        return new ChatMessageFeedbackResult(feedback.feedbackId(), "RECORDED", feedback.rating().name(), feedback.updatedAt());
    }
}
