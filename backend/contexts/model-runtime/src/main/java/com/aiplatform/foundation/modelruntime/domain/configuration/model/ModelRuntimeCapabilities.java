package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.util.Objects;

/**
 * 受控目录冻结的模型运行时能力，避免助手版本从厂商名称或模型编码推断限制。
 */
public record ModelRuntimeCapabilities(
        int contextWindow,
        Integer outputLimit,
        Integer embeddingDimension,
        Integer rerankCandidateLimit) {

    public ModelRuntimeCapabilities(int contextWindow, Integer outputLimit, Integer embeddingDimension) {
        this(contextWindow, outputLimit, embeddingDimension, null);
    }

    public ModelRuntimeCapabilities {
        if (contextWindow < 1) {
            throw new IllegalArgumentException("上下文窗口必须大于零");
        }
    }

    public static ModelRuntimeCapabilities chat(int contextWindow, int outputLimit) {
        if (outputLimit < 1 || outputLimit > contextWindow) {
            throw new IllegalArgumentException("对话输出上限必须在上下文窗口范围内");
        }
        return new ModelRuntimeCapabilities(contextWindow, outputLimit, null);
    }

    public static ModelRuntimeCapabilities embedding(int contextWindow, int embeddingDimension) {
        if (embeddingDimension < 1) {
            throw new IllegalArgumentException("嵌入维度必须大于零");
        }
        return new ModelRuntimeCapabilities(contextWindow, null, embeddingDimension);
    }

    /** 重排候选上限来自独立能力验证，不能填入向量维度或对话输出。 */
    public static ModelRuntimeCapabilities rerank(int contextWindow, int candidateLimit) {
        if (candidateLimit < 1) throw new IllegalArgumentException("重排候选上限必须大于零");
        return new ModelRuntimeCapabilities(contextWindow, null, null, candidateLimit);
    }

    /**
     * 验证能力快照与目录模型类型一致。
     *
     * @throws IllegalArgumentException 当能力缺失、混用或超出上下文窗口时拒绝。
     */
    public void requireFor(ModelType modelType) {
        Objects.requireNonNull(modelType, "模型类型不能为空");
        if (modelType == ModelType.CHAT
                && outputLimit != null
                && outputLimit > 0
                && outputLimit <= contextWindow
                && embeddingDimension == null && rerankCandidateLimit == null) {
            return;
        }
        if (modelType == ModelType.EMBEDDING
                && embeddingDimension != null
                && embeddingDimension > 0
                && outputLimit == null && rerankCandidateLimit == null) {
            return;
        }
        if (modelType == ModelType.RERANK && rerankCandidateLimit != null && rerankCandidateLimit > 0
                && outputLimit == null && embeddingDimension == null) return;
        throw new IllegalArgumentException("模型能力与模型类型不一致");
    }
}
