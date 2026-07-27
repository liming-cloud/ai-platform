package com.aiplatform.foundation.knowledgedata.application.port.out.knowledge;

import com.aiplatform.foundation.kernel.tenant.TenantId;

/**
 * 知识空间持久化端口。
 */
public interface KnowledgeSpaceRepository {

    KnowledgeSpaceCreationOutcome create(
            KnowledgeSpaceCreation creation,
            int tenantLimit);

    KnowledgeSpaceSlice findPage(TenantId tenantId, String cursor, int limit);
}
