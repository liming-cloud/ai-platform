package com.aiplatform.foundation.modelruntime.api.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 助手版本可以冻结的已发布对话部署事实。
 */
public record PublishedChatDeploymentSnapshot(
        UUID deploymentId,
        long deploymentRevision,
        int contextWindow,
        int outputLimit) {

    public PublishedChatDeploymentSnapshot {
        Objects.requireNonNull(deploymentId, "模型部署标识不能为空");
        if (deploymentRevision < 1 || contextWindow < 1 || outputLimit < 1) {
            throw new IllegalArgumentException("模型部署修订和限制必须大于零");
        }
    }
}
