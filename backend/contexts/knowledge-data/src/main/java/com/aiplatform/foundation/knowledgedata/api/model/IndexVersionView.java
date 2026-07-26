package com.aiplatform.foundation.knowledgedata.api.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 不暴露持久化和模型框架类型的索引版本视图。
 */
public record IndexVersionView(
        UUID id,
        UUID knowledgeSpaceId,
        String sourceSnapshotDigest,
        String chunkPolicy,
        UUID embeddingDeploymentId,
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
