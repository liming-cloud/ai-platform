package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserActivationRequest(@NotBlank String token, @NotBlank @Size(min = 15, max = 128) String password) {
}
