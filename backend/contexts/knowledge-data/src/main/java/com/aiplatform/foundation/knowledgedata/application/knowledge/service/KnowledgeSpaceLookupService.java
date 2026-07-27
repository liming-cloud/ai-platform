package com.aiplatform.foundation.knowledgedata.application.knowledge.service;

import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.repository.KnowledgeSpaceRepository;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 从知识空间聚合仓储投影租户内资源存在性。
 */
public final class KnowledgeSpaceLookupService implements KnowledgeSpaceLookup {

    private final KnowledgeSpaceRepository repository;

    public KnowledgeSpaceLookupService(KnowledgeSpaceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "知识空间仓储不能为空");
    }

    @Override
    public boolean exists(TenantId tenantId, long knowledgeSpaceId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        return repository.exists(tenantId, new KnowledgeSpaceId(knowledgeSpaceId));
    }
}
