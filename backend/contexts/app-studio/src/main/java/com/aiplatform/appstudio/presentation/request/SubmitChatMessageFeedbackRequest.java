package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SubmitChatMessageFeedbackRequest(
        @NotBlank String rating,
        @Size(max = 8) List<@NotBlank @Size(max = 64) String> labels,
        @Size(max = 1000) String comment
) {
}
