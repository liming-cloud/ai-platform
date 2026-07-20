package com.aiplatform.modelhub.presentation.dto;

import com.aiplatform.modelhub.application.result.ModelConnectivityTestResult;

/**
 * 模型连通性测试响应 DTO，用于展示探测结论和可审计原因。
 */
public record ModelConnectivityTestResponse(
        String providerId,
        String modelConfigId,
        String status,
        boolean connected,
        String connectivityStatus,
        String message,
        int version
) {
    public ModelConnectivityTestResponse {
        providerId = ModelHubResponseContracts.requireText(providerId, "模型供应商 ID");
        modelConfigId = ModelHubResponseContracts.requireText(modelConfigId, "模型配置 ID");
        status = ModelHubResponseContracts.requireText(status, "模型配置状态");
        connectivityStatus = ModelHubResponseContracts.requireConnectivityStatus(connectivityStatus);
        ModelHubResponseContracts.requireConnectivityResultState(connected, connectivityStatus);
        message = ModelHubResponseContracts.normalizeConnectivityMessage(connectivityStatus, message);
        if (version < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
    }

    public static ModelConnectivityTestResponse from(ModelConnectivityTestResult result) {
        return new ModelConnectivityTestResponse(
                result.providerId(),
                result.modelConfigId(),
                result.status(),
                result.connected(),
                result.connectivityStatus(),
                result.message(),
                result.version()
        );
    }
}
