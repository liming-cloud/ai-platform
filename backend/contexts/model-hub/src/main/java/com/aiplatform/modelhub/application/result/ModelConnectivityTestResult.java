package com.aiplatform.modelhub.application.result;

import com.aiplatform.modelhub.domain.ModelConfig;

/**
 * 模型连通性测试结果，供控制台展示最近一次探测状态和失败原因。
 */
public record ModelConnectivityTestResult(
        String providerId,
        String modelConfigId,
        String status,
        boolean connected,
        String connectivityStatus,
        String message,
        int version
) {
    public ModelConnectivityTestResult {
        providerId = ModelHubResultContracts.requireText(providerId, "模型供应商 ID");
        modelConfigId = ModelHubResultContracts.requireText(modelConfigId, "模型配置 ID");
        status = ModelHubResultContracts.requireText(status, "模型配置状态");
        connectivityStatus = ModelHubResultContracts.requireConnectivityStatus(connectivityStatus);
        ModelHubResultContracts.requireConnectivityResultState(connected, connectivityStatus);
        message = ModelHubResultContracts.normalizeConnectivityMessage(connectivityStatus, message);
        if (version < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
    }

    public static ModelConnectivityTestResult from(ModelConfig config, boolean connected) {
        return new ModelConnectivityTestResult(
                config.providerId(),
                config.modelConfigId(),
                config.status().name(),
                connected,
                config.connectivityStatus().name(),
                config.connectivityMessage(),
                config.version()
        );
    }
}
