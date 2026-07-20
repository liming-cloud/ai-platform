package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IamTenantStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotBlank @Size(max = 300) String reason
) {}
