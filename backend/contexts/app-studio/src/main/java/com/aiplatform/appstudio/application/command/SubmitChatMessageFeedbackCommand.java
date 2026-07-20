package com.aiplatform.appstudio.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;

public record SubmitChatMessageFeedbackCommand(
        TenantId tenantId,
        String actorId,
        String sessionId,
        String assistantMessageId,
        String rating,
        List<String> labels,
        String comment
) {
}
