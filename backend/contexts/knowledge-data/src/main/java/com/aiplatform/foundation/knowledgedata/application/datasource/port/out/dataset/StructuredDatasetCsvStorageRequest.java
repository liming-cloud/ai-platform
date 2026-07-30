package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/** 仅由服务端范围与暂存事实组成的私有 CSV 写入请求。 */
public record StructuredDatasetCsvStorageRequest(
        TenantId tenantId,
        KnowledgeSpaceId knowledgeSpaceId,
        DataSourceId dataSourceId,
        long revision,
        StagedTextContent content,
        Instant createdAt) {

    public StructuredDatasetCsvStorageRequest {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        dataSourceId = Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (revision < 1) {
            throw new IllegalArgumentException("来源修订必须为正数");
        }
        content = Objects.requireNonNull(content, "CSV 暂存内容不能为空");
        createdAt = Objects.requireNonNull(createdAt, "CSV 创建时间不能为空");
    }
}
