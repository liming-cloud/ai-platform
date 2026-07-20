package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository {
    String nextDocumentId();

    KnowledgeDocument save(KnowledgeDocument document);

    Optional<KnowledgeDocument> findById(TenantId tenantId, String knowledgeBaseId, String documentId);

    /**
     * 在当前事务内锁定唯一的租户/知识库文档行；不存在或已删除时失败关闭。
     */
    void requireForUpdate(TenantId tenantId, String knowledgeBaseId, String documentId);

    List<KnowledgeDocument> findByKnowledgeBaseId(TenantId tenantId, String knowledgeBaseId);

    void cancel(TenantId tenantId, String knowledgeBaseId, String documentId, String operatorId, Instant cancelledAt);

    void remove(TenantId tenantId, String knowledgeBaseId, String documentId, String operatorId, Instant removedAt);
}
