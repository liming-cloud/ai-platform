package com.aiplatform.foundation.modelruntime.application.configuration.facade;

import java.util.Objects;

/** 不含厂商信息的公开模型能力快照。 */
public record ModelRuntimeCapabilities(int contextWindow, Integer outputLimit, Integer embeddingDimension) {
    public ModelRuntimeCapabilities {
        if (contextWindow < 1) {
            throw new IllegalArgumentException("上下文窗口必须大于零");
        }
    }

    public static ModelRuntimeCapabilities chat(int contextWindow, int outputLimit) {
        var value = new ModelRuntimeCapabilities(contextWindow, outputLimit, null);
        value.requireFor(ModelType.CHAT);
        return value;
    }

    public static ModelRuntimeCapabilities embedding(int contextWindow, int embeddingDimension) {
        var value = new ModelRuntimeCapabilities(contextWindow, null, embeddingDimension);
        value.requireFor(ModelType.EMBEDDING);
        return value;
    }

    /** 校验能力与用途匹配，拒绝跨类型复用。 */
    public void requireFor(ModelType type) {
        Objects.requireNonNull(type, "模型类型不能为空");
        if (type == ModelType.CHAT && outputLimit != null && outputLimit > 0
                && outputLimit <= contextWindow && embeddingDimension == null) {
            return;
        }
        if (type == ModelType.EMBEDDING && embeddingDimension != null
                && embeddingDimension > 0 && outputLimit == null) {
            return;
        }
        throw new IllegalArgumentException("模型能力与模型类型不一致");
    }
}
