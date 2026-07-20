package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IamTenantUpdateRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 30) String planCode) {}
