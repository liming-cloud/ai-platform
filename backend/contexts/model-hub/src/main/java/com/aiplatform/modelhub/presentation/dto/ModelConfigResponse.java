package com.aiplatform.modelhub.presentation.dto;

import com.aiplatform.modelhub.application.result.CreateModelConfigResult;

/**
 * 模型配置创建响应 DTO，返回模型配置状态，不暴露密钥和原始探测请求。
 */
public record ModelConfigResponse(
        String providerId,
        String modelConfigId,
        String modelCode,
        String modelType,
        String status,
        String approvalRequestId,
        String connectivityStatus
) {
    public ModelConfigResponse {
        providerId = ModelHubResponseContracts.requireText(providerId, "模型供应商 ID");
        modelConfigId = ModelHubResponseContracts.requireText(modelConfigId, "模型配置 ID");
        modelCode = ModelHubResponseContracts.requireText(modelCode, "模型编码");
        modelType = ModelHubResponseContracts.requireText(modelType, "模型类型");
        status = ModelHubResponseContracts.requireText(status, "模型配置状态");
        approvalRequestId = ModelHubResponseContracts.normalizeApprovalRequestId(status, approvalRequestId);
        connectivityStatus = ModelHubResponseContracts.requireConnectivityStatus(connectivityStatus);
    }

    public static ModelConfigResponse from(CreateModelConfigResult result) {
        return new ModelConfigResponse(
                result.providerId(),
                result.modelConfigId(),
                result.modelCode(),
                result.modelType(),
                result.status(),
                result.approvalRequestId(),
                result.connectivityStatus()
        );
    }
}
