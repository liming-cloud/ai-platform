package com.aiplatform.modelhub.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建模型供应商请求 DTO，凭证材料只作为入口输入，不在响应或日志中返回。
 */
public record CreateModelProviderRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String requestId,
        @NotBlank String providerName,
        @NotBlank String providerType,
        @NotBlank String apiEndpoint,
        @NotBlank String authType,
        String credentialMaterialInput
) {
}
