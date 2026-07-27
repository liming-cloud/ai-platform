package com.aiplatform.foundation.modelruntime.api.model.embedding;

import java.util.List;
import java.util.Objects;

/**
 * 已验证数量、顺序和维度的嵌入批次。
 */
public record EmbeddingBatch(
        EmbeddingDeploymentRef deployment,
        List<EmbeddingVector> vectors) {

    public EmbeddingBatch {
        Objects.requireNonNull(deployment, "嵌入部署不能为空");
        Objects.requireNonNull(vectors, "嵌入结果不能为空");
        vectors = List.copyOf(vectors);
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("嵌入结果不能为空");
        }
        for (var index = 0; index < vectors.size(); index++) {
            var vector = Objects.requireNonNull(
                    vectors.get(index),
                    "嵌入结果不能包含空值");
            if (vector.index() != index) {
                throw new IllegalArgumentException("嵌入结果顺序不连续");
            }
            if (vector.dimension() != deployment.dimension()) {
                throw new IllegalArgumentException("嵌入结果维度与部署不一致");
            }
        }
    }
}
