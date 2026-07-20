package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.UserActivationResult;

public record UserActivationResponse(String invitationId, String userId, String tenantId, String status) {
    public static UserActivationResponse from(UserActivationResult result) {
        return new UserActivationResponse(result.invitationId(), result.userId(), result.tenantId(), result.status());
    }
}
