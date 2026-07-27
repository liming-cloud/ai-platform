package com.aiplatform.foundation.modelruntime.api.catalog.model;

/**
 * 助手版本可以冻结的已发布对话部署事实。
 */
public record PublishedChatDeploymentSnapshot(
        long deploymentId,
        long deploymentRevision,
        int contextWindow,
        int outputLimit) {

    public PublishedChatDeploymentSnapshot {
        if (deploymentId <= 0
                || deploymentRevision < 1
                || contextWindow < 1
                || outputLimit < 1) {
            throw new IllegalArgumentException("模型部署修订和限制必须大于零");
        }
    }
}
