package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplayChatRunDeadLetterRequest(
        @NotBlank @Size(max = 128) String replayRequestId,
        @NotBlank @Size(max = 512) String reason
) {
}
