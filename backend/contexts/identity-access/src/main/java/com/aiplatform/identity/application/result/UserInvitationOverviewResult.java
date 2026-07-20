package com.aiplatform.identity.application.result;

import java.time.Instant;

public record UserInvitationOverviewResult(String invitationId, String userId, String email, String status,
                                           String deliveryStatus, int deliveryAttempts, String deliveryError,
                                           Instant expiresAt, Instant createdAt) {}
