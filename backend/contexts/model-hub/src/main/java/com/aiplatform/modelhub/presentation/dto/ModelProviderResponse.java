package com.aiplatform.modelhub.presentation.dto;

import com.aiplatform.modelhub.application.result.CreateModelProviderResult;

/**
 * 模型供应商配置响应 DTO，只返回配置状态和审批请求引用。
 */
public record ModelProviderResponse(
        String providerId,
        String status,
        String approvalRequestId
) {
    public ModelProviderResponse {
        providerId = ModelHubResponseContracts.requireText(providerId, "供应商 ID");
        status = ModelHubResponseContracts.requireText(status, "供应商状态");
        approvalRequestId = ModelHubResponseContracts.requireText(approvalRequestId, "审批请求 ID");
    }

    public static ModelProviderResponse from(CreateModelProviderResult result) {
        return new ModelProviderResponse(result.providerId(), result.status(), result.approvalRequestId());
    }
}
