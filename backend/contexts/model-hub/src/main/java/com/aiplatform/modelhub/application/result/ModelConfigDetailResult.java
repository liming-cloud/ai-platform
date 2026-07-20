package com.aiplatform.modelhub.application.result;

import com.aiplatform.modelhub.domain.ModelConfig;
import java.util.Map;

/**
 * 模型配置详情结果，包含业务状态和最近一次连通性结果。
 */
public record ModelConfigDetailResult(
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
    public ModelConfigDetailResult {
        providerId = ModelHubResultContracts.requireText(providerId, "供应商 ID");
        modelConfigId = ModelHubResultContracts.requireText(modelConfigId, "模型配置 ID");
        modelCode = ModelHubResultContracts.requireText(modelCode, "模型编码");
        modelName = ModelHubResultContracts.requireText(modelName, "模型名称");
        modelType = ModelHubResultContracts.requireText(modelType, "模型类型");
        requirePositive(contextWindow, "上下文窗口");
        requirePositiveIfPresent(embeddingDimension, "向量维度");
        inputPricePer1k = ModelHubResultContracts.requireText(inputPricePer1k, "输入单价");
        outputPricePer1k = ModelHubResultContracts.requireText(outputPricePer1k, "输出单价");
        providerKind = ModelHubResultContracts.requireText(providerKind, "供应商运行时");
        temperature = normalizeOptionalText(temperature);
        topP = normalizeOptionalText(topP);
        requirePositiveIfPresent(maxTokens, "最大生成长度");
        status = ModelHubResultContracts.requireText(status, "模型配置状态");
        approvalRequestId = ModelHubResultContracts.normalizeApprovalRequestId(status, approvalRequestId);
        connectivityStatus = ModelHubResultContracts.requireConnectivityStatus(connectivityStatus);
        connectivityMessage = ModelHubResultContracts.normalizeConnectivityMessage(connectivityStatus, connectivityMessage);
        requireVersion(version);
    }

    public static ModelConfigDetailResult from(ModelConfig config) {
        return new ModelConfigDetailResult(
                config.providerId(),
                config.modelConfigId(),
                config.modelCode(),
                config.modelName(),
                config.modelType().name(),
                config.contextWindow(),
                config.embeddingDimension(),
                config.inputPricePer1k().toPlainString(),
                config.outputPricePer1k().toPlainString(),
                optionText(config.options().values(), "providerKind"),
                optionText(config.options().values(), "temperature"),
                optionText(config.options().values(), "topP"),
                optionInteger(config.options().values(), "maxTokens"),
                config.status().name(),
                config.approvalRequestId(),
                config.connectivityStatus().name(),
                config.connectivityMessage(),
                config.version()
        );
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

    private static void requireVersion(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String optionText(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static Integer optionInteger(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + "必须是整数", exception);
        }
    }
}
