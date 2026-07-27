package com.aiplatform.foundation.knowledgedata.domain.indexversion.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 索引版本冻结的嵌入部署配置。
 */
public record EmbeddingConfiguration(
        long deploymentId,
        long deploymentRevision,
        int dimension) implements ValueObject {

    public static final int MAX_VECTOR_DIMENSION = 2000;

    public EmbeddingConfiguration {
        if (deploymentId <= 0 || deploymentRevision < 1) {
            throw new IllegalArgumentException("嵌入部署修订必须大于零");
        }
        if (dimension < 1 || dimension > MAX_VECTOR_DIMENSION) {
            throw new IllegalArgumentException("嵌入维度超出平台范围");
        }
    }
}
