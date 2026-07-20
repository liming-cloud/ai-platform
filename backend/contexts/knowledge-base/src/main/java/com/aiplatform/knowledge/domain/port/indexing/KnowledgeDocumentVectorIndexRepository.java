package com.aiplatform.knowledge.domain.port.indexing;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Optional;

/**
 * 文档切片向量索引仓储端口，隔离 pgvector 写入细节和业务索引状态机。
 */
public interface KnowledgeDocumentVectorIndexRepository {
    Optional<ActiveKnowledgeIndex> findActiveIndex(
            TenantId tenantId,
            String knowledgeBaseId
    );

    void updateChunkEmbedding(
            TenantId tenantId,
            String chunkId,
            String embeddingModel,
            String indexVersion,
            float[] embedding,
            Instant indexedAt,
            String operatorId
    );

    boolean activateIfComplete(
            TenantId tenantId,
            String knowledgeBaseId,
            String embeddingModel,
            String indexVersion,
            Instant activatedAt,
            String operatorId
    );
}
