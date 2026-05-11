package org.fujay.ai.platform.tenant.interfaces.response;

public record LoginResponse(
        String tokenName,
        String tokenValue,
        String tenantId,
        String tenantCode,
        String userId,
        String username,
        String displayName) {
}
