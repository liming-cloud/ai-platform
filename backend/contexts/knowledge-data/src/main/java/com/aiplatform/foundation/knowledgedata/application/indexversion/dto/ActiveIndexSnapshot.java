package com.aiplatform.foundation.knowledgedata.application.indexversion.dto;

/**
 * 助手版本可以冻结的活动索引及嵌入部署事实。
 */
public record ActiveIndexSnapshot(
        long knowledgeSpaceId,
        long indexVersionId,
        long indexVersionRevision,
        long embeddingDeploymentId,
        long embeddingDeploymentRevision,
        int embeddingDimension) {

    public ActiveIndexSnapshot {
        if (knowledgeSpaceId <= 0 || indexVersionId <= 0) {
            throw new IllegalArgumentException("知识空间标识和索引版本标识必须是正整数");
        }
        if (embeddingDeploymentId <= 0
                || indexVersionRevision < 1
                || embeddingDeploymentRevision < 1
                || embeddingDimension < 1) {
            throw new IllegalArgumentException("索引、嵌入部署修订和维度必须大于零");
        }
    }
}
