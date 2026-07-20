package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IamTenantCreateRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,30}") String code,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 30) String planCode
) {}
