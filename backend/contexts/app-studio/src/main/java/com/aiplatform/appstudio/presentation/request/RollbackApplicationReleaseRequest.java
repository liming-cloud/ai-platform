package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RollbackApplicationReleaseRequest(
        @NotBlank @Size(max = 128) String rollbackRequestId,
        @NotBlank @Size(max = 1000) String reason
) {
}
