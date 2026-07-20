package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ApplicationAccessPolicyResult;
import java.time.Instant;

public record ApplicationAccessPolicyResponse(
        String applicationId,
        int authorizedUserCount,
        int authorizedRoleCount,
        String auditEventId,
        Instant updatedAt
) {
    public static ApplicationAccessPolicyResponse from(ApplicationAccessPolicyResult result) {
        return new ApplicationAccessPolicyResponse(
                result.applicationId(),
                result.authorizedUserCount(),
                result.authorizedRoleCount(),
                result.auditEventId(),
                result.updatedAt()
        );
    }
}
