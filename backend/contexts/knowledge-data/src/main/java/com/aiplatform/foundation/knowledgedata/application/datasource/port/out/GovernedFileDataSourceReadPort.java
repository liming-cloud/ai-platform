package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedFileDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * FILE 来源的受控只读端口。
 *
 * <p>实现必须同时以租户、知识空间和 FILE 类型过滤，并仅投影来源任务恢复所需的非敏感事实。</p>
 */
public interface GovernedFileDataSourceReadPort {

    PageResult<GovernedFileDataSourceSummary> list(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            PageQuery page);
}
