package com.aiplatform.modelhub.presentation.dto;

/**
 * 模型供应商详情响应 DTO，不返回密钥或密钥引用。
 */
public record ModelProviderDetailResponse(
        String providerId,
        String providerCode,
        String providerName,
        String endpoint,
        String status,
        String approvalRequestId,
        int version
) {
    public ModelProviderDetailResponse {
        providerId = ModelHubResponseContracts.requireText(providerId, "供应商 ID");
        providerCode = ModelHubResponseContracts.requireText(providerCode, "供应商编码");
        providerName = ModelHubResponseContracts.requireText(providerName, "供应商名称");
        endpoint = ModelHubResponseContracts.requireText(endpoint, "供应商 Endpoint");
        status = ModelHubResponseContracts.requireText(status, "供应商状态");
        approvalRequestId = ModelHubResponseContracts.normalizeApprovalRequestId(status, approvalRequestId);
        if (version < 0) {
            throw new IllegalArgumentException("模型供应商版本不能小于 0");
        }
    }
}
