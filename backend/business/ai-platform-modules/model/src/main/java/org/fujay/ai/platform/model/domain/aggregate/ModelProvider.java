package org.fujay.ai.platform.model.domain.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.ddd.AggregateRoot;
import org.fujay.ai.platform.shared.error.PlatformException;

public record ModelProvider(
        Long id,
        Long tenantId,
        String providerName,
        ProviderType providerType,
        String baseUrl,
        String apiKeyCiphertext,
        String apiKeyLast4,
        String status,
        BigDecimal budgetUsd,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ConfiguredModel> models) implements AggregateRoot {

    public ModelProvider {
        requireId(id, "供应商标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        requireNotBlank(providerName, "供应商名称不能为空");
        providerType = Objects.requireNonNull(providerType, "供应商类型不能为空");
        requireNotBlank(baseUrl, "供应商 baseUrl 不能为空");
        requireNotBlank(apiKeyCiphertext, "供应商密钥密文不能为空");
        requireNotBlank(apiKeyLast4, "供应商密钥摘要不能为空");
        requireNotBlank(status, "供应商状态不能为空");
        createdAt = Objects.requireNonNull(createdAt, "供应商创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "供应商更新时间不能为空");
        models = List.copyOf(models == null ? List.of() : models);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个模型");
        }
        for (ConfiguredModel model : models) {
            if (!tenantId.equals(model.tenantId()) || !id.equals(model.providerId())) {
                throw new IllegalArgumentException("模型与供应商归属不一致");
            }
            model.validateFor(providerType);
        }
    }

    public ConfiguredModel requireModel(Long modelId) {
        return models.stream()
                .filter(model -> model.id().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new PlatformException(ModelErrorCode.MODEL_NOT_FOUND));
    }

    public ConfiguredModel requireChatModel(Long modelId) {
        ConfiguredModel model = requireModel(modelId);
        model.requireChatCapability();
        return model;
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
