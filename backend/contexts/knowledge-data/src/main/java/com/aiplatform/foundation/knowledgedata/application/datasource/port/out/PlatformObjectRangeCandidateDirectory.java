package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.PlatformObjectRangeCandidate;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 按租户和知识空间读取名称化授权对象范围的分页契约。 */
@FunctionalInterface
public interface PlatformObjectRangeCandidateDirectory {
    PageResult<PlatformObjectRangeCandidate> page(
            TenantId tenantId, KnowledgeSpaceId knowledgeSpaceId, PageQuery page);
}
