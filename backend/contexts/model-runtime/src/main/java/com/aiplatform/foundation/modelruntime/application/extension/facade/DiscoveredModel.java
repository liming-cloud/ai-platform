package com.aiplatform.foundation.modelruntime.application.extension.facade;

import java.util.Objects;
import java.util.Set;

/** 厂商发现扩展返回的模型候选契约。 */
public record DiscoveredModel(String modelCode, ModelType modelType, Set<ModelCapability> capabilities) {
    public DiscoveredModel {
        Objects.requireNonNull(modelCode, "发现模型代码不能为空");
        if (modelCode.isBlank() || !modelCode.equals(modelCode.trim()) || modelCode.length() > 200) {
            throw new IllegalArgumentException("发现模型代码格式不正确");
        }
        modelType = Objects.requireNonNull(modelType, "发现模型类型不能为空");
        // 本契约仍属于既有租户发现；平台重排草稿不能隐式开放租户运行能力。
        if (modelType == ModelType.RERANK) throw new IllegalArgumentException("租户重排候选合同尚未开放");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "发现模型能力不能为空"));
        if (capabilities.isEmpty() || capabilities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("发现模型能力不能为空或包含空值");
        }
        if (modelType == ModelType.CHAT && !capabilities.contains(ModelCapability.CHAT)) {
            throw new IllegalArgumentException("对话发现模型必须声明对话能力");
        }
        if (modelType == ModelType.EMBEDDING && !capabilities.contains(ModelCapability.EMBEDDING)) {
            throw new IllegalArgumentException("嵌入发现模型必须声明嵌入能力");
        }
    }

}
