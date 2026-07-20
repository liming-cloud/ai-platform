package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录接口请求 DTO，保持为 presentation 顶层类型，避免 Controller 内部类承载协议数据。
 */
public record LoginRequest(
        @NotBlank String tenantCode,
        @NotBlank String username,
        @NotBlank String password
) {
}
