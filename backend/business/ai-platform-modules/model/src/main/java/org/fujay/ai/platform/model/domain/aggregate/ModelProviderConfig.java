package org.fujay.ai.platform.model.domain.aggregate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.error.PlatformException;

public record ModelProviderConfig(
        Long providerId,
        Long tenantId,
        String providerName,
        ProviderType providerType,
        String baseUrl,
        String apiKeyCiphertext,
        String apiKeyLast4,
        BigDecimal budgetUsd,
        List<ModelDefinition> models) {

    public ModelProviderConfig {
        requireId(providerId, "供应商标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        requireNotBlank(providerName, "供应商名称不能为空");
        ProviderType checkedProviderType = Objects.requireNonNull(providerType, "供应商类型不能为空");
        requireNotBlank(apiKeyCiphertext, "供应商密钥密文不能为空");
        models = List.copyOf(models == null ? List.of() : models);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个模型");
        }
        for (ModelDefinition model : models) {
            validateModel(checkedProviderType, model);
        }
    }

    public ModelDefinition requireModel(Long modelId) {
        return models.stream()
                .filter(model -> model.modelId().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ModelErrorCode.MODEL_NOT_FOUND));
    }

    private static void validateModel(ProviderType providerType, ModelDefinition model) {
        requireId(model.modelId(), "模型标识不能为空");
        requireNotBlank(model.modelCode(), "模型编码不能为空");
        requireNotBlank(model.displayName(), "模型名称不能为空");
        Objects.requireNonNull(model.capabilityType(), "模型能力类型不能为空");
        model.defaults().validate(providerType, model.capabilityType());
        if (providerType == ProviderType.OPENAI) {
            validateOpenAiCapability(model.modelCode(), model.capabilityType());
        }
    }

    private static void validateOpenAiCapability(String modelCode, ModelCapabilityType capabilityType) {
        String normalized = modelCode.toLowerCase(Locale.ROOT);
        boolean embeddingModel = normalized.startsWith("text-embedding-");
        if (capabilityType == ModelCapabilityType.EMBEDDING && !embeddingModel) {
            throw new PlatformException(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
        }
        if (capabilityType == ModelCapabilityType.CHAT && embeddingModel) {
            throw new PlatformException(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record ModelDefinition(
            Long modelId,
            String modelCode,
            String displayName,
            ModelCapabilityType capabilityType,
            ModelParameterDefaults defaults) {

        public ModelDefinition {
            defaults = defaults == null
                    ? new ModelParameterDefaults(null, null, null, null, null, null, null, null, null)
                    : defaults;
        }
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
