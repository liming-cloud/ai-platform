package com.aiplatform.foundation.knowledgedata.domain.indexversion;

import java.util.Objects;
import java.util.UUID;

/**
 * 索引版本冻结的嵌入部署配置。
 */
public record EmbeddingConfiguration(
        UUID deploymentId,
        long deploymentRevision,
        int dimension) {

    public static final int MAX_VECTOR_DIMENSION = 2000;

    public EmbeddingConfiguration {
        Objects.requireNonNull(deploymentId, "嵌入部署标识不能为空");
        if (deploymentRevision < 1) {
            throw new IllegalArgumentException("嵌入部署修订必须大于零");
        }
        if (dimension < 1 || dimension > MAX_VECTOR_DIMENSION) {
            throw new IllegalArgumentException("嵌入维度超出平台范围");
        }
    }
}
