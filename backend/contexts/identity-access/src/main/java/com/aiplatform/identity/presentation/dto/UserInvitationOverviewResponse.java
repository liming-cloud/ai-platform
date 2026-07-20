package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.UserInvitationOverviewResult;
import java.time.Instant;

public record UserInvitationOverviewResponse(String invitationId, String userId, String email, String status,
                                             String statusLabel, String deliveryStatus, String deliveryStatusLabel,
                                             int deliveryAttempts, String deliveryError, Instant expiresAt, Instant createdAt) {
    public static UserInvitationOverviewResponse from(UserInvitationOverviewResult result) {
        return new UserInvitationOverviewResponse(result.invitationId(), result.userId(), result.email(), result.status(),
                switch (result.status()) { case "PENDING" -> "待激活"; case "ACCEPTED" -> "已激活"; case "REVOKED" -> "已撤销"; default -> result.status(); },
                result.deliveryStatus(), switch (result.deliveryStatus()) { case "QUEUED" -> "待发送"; case "DELIVERED" -> "已送达"; case "FAILED" -> "发送失败"; default -> result.deliveryStatus(); },
                result.deliveryAttempts(), result.deliveryError(), result.expiresAt(), result.createdAt());
    }
}
