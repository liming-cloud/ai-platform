package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;

/**
 * 知识文档切片仓储端口，负责维护解析后切片的持久化替换与查询边界。
 */
public interface KnowledgeDocumentChunkRepository {
    String nextChunkId();

    List<KnowledgeDocumentChunk> saveAll(List<KnowledgeDocumentChunk> chunks);

    List<KnowledgeDocumentChunk> findByDocumentId(TenantId tenantId, String documentId);

    void replaceByDocumentId(TenantId tenantId, String knowledgeBaseId, String documentId, List<KnowledgeDocumentChunk> chunks);
}
