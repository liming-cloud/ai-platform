package com.aiplatform.modelhub.presentation.dto;

/**
 * 模型配置详情响应 DTO，包含状态和最近一次连通性说明。
 */
public record ModelConfigDetailResponse(
        String providerId,
        String modelConfigId,
        String modelCode,
        String modelName,
        String modelType,
        Integer contextWindow,
        Integer embeddingDimension,
        String inputPricePer1k,
        String outputPricePer1k,
        String providerKind,
        String temperature,
        String topP,
        Integer maxTokens,
        String status,
        String approvalRequestId,
        String connectivityStatus,
        String connectivityMessage,
        int version
) {
    public ModelConfigDetailResponse {
        providerId = ModelHubResponseContracts.requireText(providerId, "供应商 ID");
        modelConfigId = ModelHubResponseContracts.requireText(modelConfigId, "模型配置 ID");
        modelCode = ModelHubResponseContracts.requireText(modelCode, "模型编码");
        modelName = ModelHubResponseContracts.requireText(modelName, "模型名称");
        modelType = ModelHubResponseContracts.requireText(modelType, "模型类型");
        requirePositive(contextWindow, "上下文窗口");
        requirePositiveIfPresent(embeddingDimension, "向量维度");
        inputPricePer1k = ModelHubResponseContracts.requireText(inputPricePer1k, "输入单价");
        outputPricePer1k = ModelHubResponseContracts.requireText(outputPricePer1k, "输出单价");
        providerKind = ModelHubResponseContracts.requireText(providerKind, "供应商运行时");
        temperature = normalizeOptionalText(temperature);
        topP = normalizeOptionalText(topP);
        requirePositiveIfPresent(maxTokens, "最大生成长度");
        status = ModelHubResponseContracts.requireText(status, "模型配置状态");
        approvalRequestId = ModelHubResponseContracts.normalizeApprovalRequestId(status, approvalRequestId);
        connectivityStatus = ModelHubResponseContracts.requireConnectivityStatus(connectivityStatus);
        connectivityMessage = ModelHubResponseContracts.normalizeConnectivityMessage(connectivityStatus, connectivityMessage);
        if (version < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
    }

    private static void requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

    private static void requirePositiveIfPresent(Integer value, String fieldName) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
