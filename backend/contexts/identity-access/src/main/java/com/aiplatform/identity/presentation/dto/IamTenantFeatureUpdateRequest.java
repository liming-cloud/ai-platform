package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * IAM 租户功能更新请求 DTO，仅允许状态化维护 feature 启停。
 */
public record IamTenantFeatureUpdateRequest(
        @NotNull Boolean enabled,
        @NotBlank @Size(max = 300) String reason
) {
}
