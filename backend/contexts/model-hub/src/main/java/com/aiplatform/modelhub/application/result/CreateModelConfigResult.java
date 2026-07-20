package com.aiplatform.modelhub.application.result;

import com.aiplatform.modelhub.domain.ModelConfig;

/**
 * 创建模型配置结果，返回模型配置主键、业务状态和连通性状态。
 */
public record CreateModelConfigResult(
        String providerId,
        String modelConfigId,
        String modelCode,
        String modelType,
        String status,
        String approvalRequestId,
        String connectivityStatus
) {
    public CreateModelConfigResult {
        providerId = ModelHubResultContracts.requireText(providerId, "模型供应商 ID");
        modelConfigId = ModelHubResultContracts.requireText(modelConfigId, "模型配置 ID");
        modelCode = ModelHubResultContracts.requireText(modelCode, "模型编码");
        modelType = ModelHubResultContracts.requireText(modelType, "模型类型");
        status = ModelHubResultContracts.requireText(status, "模型配置状态");
        approvalRequestId = ModelHubResultContracts.normalizeApprovalRequestId(status, approvalRequestId);
        connectivityStatus = ModelHubResultContracts.requireConnectivityStatus(connectivityStatus);
    }

    public static CreateModelConfigResult from(ModelConfig config) {
        return new CreateModelConfigResult(
                config.providerId(),
                config.modelConfigId(),
                config.modelCode(),
                config.modelType().name(),
                config.status().name(),
                config.approvalRequestId(),
                config.connectivityStatus().name()
        );
    }
}
