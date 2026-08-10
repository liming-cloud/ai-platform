package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.Set;

/**
 * 发现事实内可由受信任厂商适配器安全声明的模型候选，不构成模型发布证明。
 */
public record DiscoveredModel(
        String modelCode,
        ModelType modelType,
        Set<ModelCapability> capabilities) implements ValueObject {

    public DiscoveredModel {
        Objects.requireNonNull(modelCode, "发现模型代码不能为空");
        if (modelCode.isBlank() || !modelCode.equals(modelCode.trim()) || modelCode.length() > 200) {
            throw new IllegalArgumentException("发现模型代码格式不正确");
        }
        modelType = Objects.requireNonNull(modelType, "发现模型类型不能为空");
        capabilities = immutableCapabilities(capabilities);
        if (modelType == ModelType.CHAT && !capabilities.contains(ModelCapability.CHAT)) {
            throw new IllegalArgumentException("对话发现模型必须声明对话能力");
        }
        if (modelType == ModelType.EMBEDDING && !capabilities.contains(ModelCapability.EMBEDDING)) {
            throw new IllegalArgumentException("嵌入发现模型必须声明嵌入能力");
        }
    }

    private static Set<ModelCapability> immutableCapabilities(Set<ModelCapability> values) {
        values = Objects.requireNonNull(values, "发现模型能力不能为空");
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("发现模型能力不能为空或包含空值");
        }
        return Set.copyOf(values);
    }
}
