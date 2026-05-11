package org.fujay.ai.platform.tenant.application.dto;

public record AuthenticatedSession(
        String tokenName,
        String tokenValue,
        Long tenantId,
        String tenantCode,
        Long userId,
        String username,
        String displayName) {
}
