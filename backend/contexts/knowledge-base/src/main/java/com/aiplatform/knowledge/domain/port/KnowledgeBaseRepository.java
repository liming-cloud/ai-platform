package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * 知识库聚合仓储端口，只暴露创建、保存和按请求 ID 加载能力；检索授权过滤由领域策略承担。
 */
public interface KnowledgeBaseRepository {
    String nextKnowledgeBaseId();

    KnowledgeBase save(KnowledgeBase knowledgeBase);

    Optional<KnowledgeBase> findById(TenantId tenantId, String knowledgeBaseId);

    boolean existsById(TenantId tenantId, String knowledgeBaseId);

    List<KnowledgeBaseListItemProjection> findListItemsByTenant(TenantId tenantId);
}
