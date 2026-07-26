package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.util.Objects;

/**
 * 原子登记已持久化文本内容的请求。
 */
public record TextContentAttachment(
        TenantId tenantId,
        KnowledgeSpaceId knowledgeSpaceId,
        DataSourceId dataSourceId,
        long expectedVersion,
        TextContentReference content,
        String idempotencyKey,
        String requestFingerprint) {

    public TextContentAttachment {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
        Objects.requireNonNull(content, "文本内容引用不能为空");
        idempotencyKey = TextDataSourceCreation.requireIdempotencyKey(idempotencyKey);
        requestFingerprint = TextDataSourceCreation.requireFingerprint(requestFingerprint);
    }
}
