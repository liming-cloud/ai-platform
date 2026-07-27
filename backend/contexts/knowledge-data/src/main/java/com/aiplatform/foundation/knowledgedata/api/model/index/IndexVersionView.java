package com.aiplatform.foundation.knowledgedata.api.model.index;

import java.time.Instant;

/**
 * 不暴露持久化和模型框架类型的索引版本视图。
 */
public record IndexVersionView(
        String id,
        String knowledgeSpaceId,
        String sourceSnapshotDigest,
        String chunkPolicy,
        String embeddingDeploymentId,
        long embeddingDeploymentRevision,
        int embeddingDimension,
        String status,
        long version,
        String buildDigest,
        int chunkCount,
        String failureCode,
        Instant createdAt,
        Instant readyAt,
        Instant activatedAt,
        Instant supersededAt) {
}
