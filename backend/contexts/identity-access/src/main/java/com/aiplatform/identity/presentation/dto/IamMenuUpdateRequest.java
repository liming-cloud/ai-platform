package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * IAM 菜单更新请求 DTO，承载平台菜单事实的可维护字段。
 */
public record IamMenuUpdateRequest(
        @NotBlank String name,
        @NotBlank String path,
        @NotBlank String routeName,
        @NotBlank String componentKey,
        String icon,
        @NotBlank String requiredPermissionCode,
        @NotNull Integer sort,
        @NotBlank String status
) {
}
