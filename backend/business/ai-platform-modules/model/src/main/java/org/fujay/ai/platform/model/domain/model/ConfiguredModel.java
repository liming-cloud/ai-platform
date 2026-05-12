package org.fujay.ai.platform.model.domain.model;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.ddd.Entity;
import org.fujay.ai.platform.shared.error.PlatformException;

public record ConfiguredModel(
        Long id,
        Long tenantId,
        Long providerId,
        String modelCode,
        String displayName,
        ModelCapabilityType capabilityType,
        String status,
        ModelParameterDefaults defaults,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) implements Entity {

    public ConfiguredModel {
        requireId(id, "模型标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        requireId(providerId, "供应商标识不能为空");
        requireNotBlank(modelCode, "模型编码不能为空");
        requireNotBlank(displayName, "模型名称不能为空");
        capabilityType = Objects.requireNonNull(capabilityType, "模型能力类型不能为空");
        requireNotBlank(status, "模型状态不能为空");
        defaults = defaults == null
                ? new ModelParameterDefaults(null, null, null, null, null, null, null, null, null)
                : defaults;
    }

    public String identity() {
        return modelCode + "::" + capabilityType.name();
    }

    public void validateFor(ProviderType providerType) {
        defaults.validate(providerType, capabilityType);
        if (providerType == ProviderType.OPENAI) {
            validateOpenAiCapability();
        }
    }

    public void requireChatCapability() {
        if (capabilityType != ModelCapabilityType.CHAT) {
            throw new PlatformException(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
        }
    }

    private void validateOpenAiCapability() {
        String normalized = modelCode.toLowerCase(Locale.ROOT);
        boolean embeddingModel = normalized.startsWith("text-embedding-");
        if (capabilityType == ModelCapabilityType.EMBEDDING && !embeddingModel) {
            throw new PlatformException(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
        }
        if (capabilityType == ModelCapabilityType.CHAT && embeddingModel) {
            throw new PlatformException(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
        }
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
