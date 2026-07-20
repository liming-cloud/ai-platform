package com.aiplatform.modelhub.application.command;

import com.aiplatform.modelhub.domain.ModelPriceContract;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestId;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApprovalEvidenceSecretPolicy;
import com.aiplatform.shared.kernel.ModelApprovalRuntimeOptions;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 创建模型配置命令，入口层负责填充服务端租户、操作者和模型运行参数。
 */
public record CreateModelConfigCommand(
        TenantId tenantId,
        String operatorId,
        String requestId,
        String providerId,
        String modelCode,
        String modelName,
        String modelType,
        Integer contextWindow,
        Integer embeddingDimension,
        BigDecimal inputPricePer1k,
        BigDecimal outputPricePer1k,
        Map<String, Object> options
) {
    public CreateModelConfigCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作者 ID");
        requestId = ModelCreationRequestId.require(requestId);
        providerId = requireText(providerId, "模型供应商 ID");
        modelCode = requireText(modelCode, "模型编码");
        modelName = requireText(modelName, "模型名称");
        modelType = requireText(modelType, "模型类型");
        ApprovalEvidenceSecretPolicy.requireSecretFree(modelCode, "模型编码");
        ApprovalEvidenceSecretPolicy.requireSecretFree(modelName, "模型名称");
        requirePositive(contextWindow, "上下文窗口");
        inputPricePer1k = ModelPriceContract.requireDatabaseValue(inputPricePer1k, "输入单价");
        outputPricePer1k = ModelPriceContract.requireDatabaseValue(outputPricePer1k, "输出单价");
        options = ModelApprovalRuntimeOptions.validateAndNormalize(
                copyRequiredMap(options, "模型运行时 options")
        );
        ApprovalEvidenceSecretPolicy.requireSecretFreeValue(options, "模型运行时 options");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requirePositive(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

    private static <K, V> Map<K, V> copyRequiredMap(Map<K, V> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        Map<K, V> copy = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : values.entrySet()) {
            copy.put(
                    requireElement(entry.getKey(), fieldName + " key"),
                    requireElement(entry.getValue(), fieldName + " value")
            );
        }
        return Map.copyOf(copy);
    }

    private static <T> T requireElement(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
