package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.UserInvitationResult;
import java.time.Instant;

public record UserInvitationResponse(String invitationId, String userId, String status, String deliveryStatus, Instant expiresAt) {
    public static UserInvitationResponse from(UserInvitationResult result) {
        return new UserInvitationResponse(result.invitationId(), result.userId(), result.status(), result.deliveryStatus(), result.expiresAt());
    }
}
