package com.aiplatform.identity.application.result;

public record UserActivationResult(String invitationId, String userId, String tenantId, String status) {
}
