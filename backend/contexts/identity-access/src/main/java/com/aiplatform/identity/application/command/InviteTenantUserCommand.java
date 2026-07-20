package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;

public record InviteTenantUserCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String username,
        String displayName,
        String email,
        String roleId
) {
}
