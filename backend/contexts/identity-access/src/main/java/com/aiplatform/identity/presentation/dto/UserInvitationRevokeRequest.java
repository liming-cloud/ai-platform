package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserInvitationRevokeRequest(
        @NotBlank(message = "撤销原因不能为空")
        @Size(max = 500, message = "撤销原因不能超过 500 个字符")
        String reason
) {
}
