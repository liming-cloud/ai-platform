package com.aiplatform.foundation.modelruntime.api.model.embedding;

/**
 * 一次嵌入调用绑定的不可变模型部署修订。
 */
public record EmbeddingDeploymentRef(
        long deploymentId,
        long revision,
        int dimension) {

    public EmbeddingDeploymentRef {
        if (deploymentId <= 0 || revision < 1) {
            throw new IllegalArgumentException("嵌入部署修订必须大于零");
        }
        if (dimension < 1 || dimension > 2000) {
            throw new IllegalArgumentException("嵌入维度超出平台范围");
        }
    }
}
