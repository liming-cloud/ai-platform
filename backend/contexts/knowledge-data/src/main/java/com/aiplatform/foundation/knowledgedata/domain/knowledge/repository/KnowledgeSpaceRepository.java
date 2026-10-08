package com.aiplatform.foundation.knowledgedata.domain.knowledge.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;

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

    boolean exists(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId);
    /** 当前非删除知识库的名称；名称不代表内容读取授权。 */
    java.util.Optional<String> findName(TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId);
}
