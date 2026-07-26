package com.aiplatform.foundation.modelruntime.api.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 索引版本可以冻结的已发布嵌入部署事实。
 */
public record PublishedEmbeddingDeploymentSnapshot(
        UUID deploymentId,
        long deploymentRevision,
        int dimension) {

    public PublishedEmbeddingDeploymentSnapshot {
        Objects.requireNonNull(deploymentId, "模型部署标识不能为空");
        if (deploymentRevision < 1 || dimension < 1) {
            throw new IllegalArgumentException("嵌入部署修订和维度必须大于零");
        }
    }
}
