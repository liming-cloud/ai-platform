package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 模型配置创建领域服务，集中约束模型类型解析、初始状态和审批前可用性。
 */
public class ModelConfigCreationService {
    public ModelConfig createModelConfig(
            TenantId tenantId,
            String modelConfigId,
            ModelProvider provider,
            String modelCode,
            String modelName,
            String modelType,
            Integer contextWindow,
            Integer embeddingDimension,
            BigDecimal inputPricePer1k,
            BigDecimal outputPricePer1k,
            Map<String, Object> options,
            String approvalRequestId,
            Instant createdAt
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(provider, "模型供应商不能为空");
        if (!tenantId.equals(provider.tenantId())) {
            throw new IllegalArgumentException("模型供应商不属于当前租户");
        }
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        ModelRuntimeOptions runtimeOptions = new ModelRuntimeOptions(options);
        ModelProviderKinds.requireCompatibleRuntimeKind(provider.providerCode(), runtimeOptions.providerKind());
        return new ModelConfig(
                tenantId,
                modelConfigId,
                provider.providerId(),
                modelCode,
                modelName,
                parseModelType(modelType),
                contextWindow,
                embeddingDimension,
                inputPricePer1k,
                outputPricePer1k,
                initialStatus(provider),
                approvalRequestId,
                runtimeOptions,
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                createdAt,
                createdAt
        );
    }

    private static ModelConfigStatus initialStatus(ModelProvider provider) {
        if (provider.status() != ModelProviderStatus.ACTIVE || provider.approvalRequiredForConfigChange()) {
            return ModelConfigStatus.PENDING_APPROVAL;
        }
        return ModelConfigStatus.INACTIVE;
    }

    private static ModelType parseModelType(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        try {
            return ModelType.valueOf(modelType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型类型不支持：" + modelType, exception);
        }
    }
}
