package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;

/**
 * 文本数据源事务边界。
 */
public interface TextDataSourceRepository {

    TextDataSourceMutationOutcome create(
            TextDataSourceCreation creation,
            int tenantLimit);

    TextDataSourceMutationOutcome attach(TextContentAttachment attachment);

    TextDataSourceMutationOutcome publish(TextDataSourcePublication publication);

    TextDataSourceSlice findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            String cursor,
            int limit);
}
