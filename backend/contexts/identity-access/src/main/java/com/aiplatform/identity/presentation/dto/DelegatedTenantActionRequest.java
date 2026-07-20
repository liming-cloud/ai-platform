package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 平台管理员代管租户动作请求。
 */
public record DelegatedTenantActionRequest(
        @NotBlank String actionType,
        String targetRoleId,
        String targetUserId,
        List<String> permissionCodes,
        @NotBlank @Size(max = 512) String reason,
        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
