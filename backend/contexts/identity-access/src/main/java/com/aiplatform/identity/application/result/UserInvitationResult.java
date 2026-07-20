package com.aiplatform.identity.application.result;

import java.time.Instant;

public record UserInvitationResult(
        String invitationId,
        String userId,
        String status,
        String deliveryStatus,
        Instant expiresAt
) {
}
