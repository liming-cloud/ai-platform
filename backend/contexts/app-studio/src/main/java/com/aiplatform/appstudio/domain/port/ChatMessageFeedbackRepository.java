package com.aiplatform.appstudio.domain.port;

import com.aiplatform.appstudio.domain.ChatMessageFeedback;

public interface ChatMessageFeedbackRepository {
    ChatMessageFeedback save(ChatMessageFeedback feedback);
}
