package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import java.time.Instant;

public record PermissionRequestResponse(
        String requestId,
        String requesterId,
        String resourceType,
        String resourceId,
        String permissionCode,
        String reason,
        String status,
        String reviewerId,
        String decisionComment,
        Instant createdAt,
        Instant updatedAt
) {
    public static PermissionRequestResponse from(PermissionRequest request) {
        return new PermissionRequestResponse(request.requestId(), request.requesterId(), request.resourceType(),
                request.resourceId(), request.permissionCode(), request.reason(), request.status().name(),
                request.reviewerId(), request.decisionComment(), request.createdAt(), request.updatedAt());
    }
}
