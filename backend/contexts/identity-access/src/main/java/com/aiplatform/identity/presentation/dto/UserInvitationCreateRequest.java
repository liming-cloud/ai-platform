package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserInvitationCreateRequest(
        @NotBlank String username,
        @NotBlank String displayName,
        @NotBlank @Email String email,
        @NotBlank String roleId
) {
}
