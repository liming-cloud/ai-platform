package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishAiApplicationRequest(
        @NotBlank @Size(max = 128) String publishRequestId,
        @Size(max = 512) String reason
) {
}
