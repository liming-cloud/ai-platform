package com.aiplatform.foundation.knowledgedata.application.port.out.content;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 由服务端事实生成对象键并写入私有存储的请求。
 */
public record TextContentStorageRequest(
        TenantId tenantId,
        KnowledgeSpaceId knowledgeSpaceId,
        DataSourceId dataSourceId,
        int contentVersion,
        StagedTextContent content,
        Instant createdAt) {

    public TextContentStorageRequest {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (contentVersion < 1) {
            throw new IllegalArgumentException("内容版本必须大于零");
        }
        Objects.requireNonNull(content, "暂存文本不能为空");
        Objects.requireNonNull(createdAt, "内容创建时间不能为空");
    }
}
