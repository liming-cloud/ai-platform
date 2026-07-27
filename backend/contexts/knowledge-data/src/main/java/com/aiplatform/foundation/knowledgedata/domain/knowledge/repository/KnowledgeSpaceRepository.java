package com.aiplatform.foundation.knowledgedata.domain.knowledge.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpace;

/**
 * 知识空间持久化端口。
 */
public interface KnowledgeSpaceRepository {

    KnowledgeSpaceCreationOutcome create(
            KnowledgeSpaceCreation creation,
            int tenantLimit);

    KnowledgeSpacePage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize);
}
