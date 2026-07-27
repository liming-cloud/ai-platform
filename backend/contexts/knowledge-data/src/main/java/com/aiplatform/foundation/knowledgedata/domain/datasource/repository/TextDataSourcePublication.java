package com.aiplatform.foundation.knowledgedata.domain.datasource.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 原子发布文本数据源的请求。
 */
public record TextDataSourcePublication(
        TenantId tenantId,
        KnowledgeSpaceId knowledgeSpaceId,
        DataSourceId dataSourceId,
        long expectedVersion,
        String idempotencyKey,
        String requestFingerprint,
        Instant publishedAt) {

    public TextDataSourcePublication {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
        idempotencyKey = TextDataSourceCreation.requireIdempotencyKey(idempotencyKey);
        requestFingerprint = TextDataSourceCreation.requireFingerprint(requestFingerprint);
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
    }
}
