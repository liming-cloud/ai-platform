package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionRequestCreateRequest(
        @NotBlank @Size(max = 64) String resourceType,
        @NotBlank @Size(max = 128) String resourceId,
        @NotBlank @Size(max = 128) String permissionCode,
        @NotBlank @Size(max = 512) String reason
) {
}
