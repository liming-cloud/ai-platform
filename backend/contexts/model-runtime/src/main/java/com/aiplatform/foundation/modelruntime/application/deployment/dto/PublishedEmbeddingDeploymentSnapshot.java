package com.aiplatform.foundation.modelruntime.application.deployment.dto;

/**
 * 索引版本可以冻结的已发布嵌入部署事实。
 */
public record PublishedEmbeddingDeploymentSnapshot(
        long deploymentId,
        long deploymentRevision,
        int dimension) {

    public PublishedEmbeddingDeploymentSnapshot {
        if (deploymentId <= 0 || deploymentRevision < 1 || dimension < 1) {
            throw new IllegalArgumentException("嵌入部署修订和维度必须大于零");
        }
    }
}
