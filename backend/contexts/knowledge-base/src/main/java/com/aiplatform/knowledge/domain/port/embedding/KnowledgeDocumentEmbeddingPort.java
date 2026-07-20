package com.aiplatform.knowledge.domain.port.embedding;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.TenantId;

/**
 * 文档切片向量化端口，和查询向量端口分离，便于后续批量、重试和成本治理。
 */
public interface KnowledgeDocumentEmbeddingPort {
    float[] embedChunk(TenantId tenantId, KnowledgeModelRuntimeDescriptor runtimeDescriptor, String chunkContent);
}
