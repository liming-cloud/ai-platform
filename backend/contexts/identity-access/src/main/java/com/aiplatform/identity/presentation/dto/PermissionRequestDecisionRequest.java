package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionRequestDecisionRequest(
        @NotBlank String action,
        @NotBlank @Size(max = 512) String comment
) {
}
