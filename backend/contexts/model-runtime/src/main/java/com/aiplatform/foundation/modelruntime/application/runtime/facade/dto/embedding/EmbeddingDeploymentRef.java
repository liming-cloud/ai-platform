package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding;

/**
 * 一次嵌入调用绑定的不可变模型部署修订。
 */
public record EmbeddingDeploymentRef(
        long deploymentId,
        long revision,
        int dimension) {

    // 与 `EmbeddingVector` 保持一致：当前精确检索使用 pgvector `vector` 的 16000 维存储上限。
    private static final int MAX_DIMENSION = 16000;

    public EmbeddingDeploymentRef {
        if (deploymentId <= 0 || revision < 1) {
            throw new IllegalArgumentException("嵌入部署修订必须大于零");
        }
        if (dimension < 1 || dimension > MAX_DIMENSION) {
            throw new IllegalArgumentException("嵌入维度超出平台范围");
        }
    }
}
