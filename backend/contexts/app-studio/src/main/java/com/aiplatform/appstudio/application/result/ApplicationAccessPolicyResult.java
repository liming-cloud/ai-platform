package com.aiplatform.appstudio.application.result;

import java.time.Instant;

public record ApplicationAccessPolicyResult(
        String applicationId,
        int authorizedUserCount,
        int authorizedRoleCount,
        String auditEventId,
        Instant updatedAt
) {
}
