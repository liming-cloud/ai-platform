package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunPublishPreflightRequest(
        @NotBlank @Size(max = 128) String publishRequestId
) {
}
