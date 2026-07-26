package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 列表查询所需的只读文本数据源事实。
 */
public record TextDataSourceSummary(
        DataSourceId id,
        KnowledgeSpaceId knowledgeSpaceId,
        String name,
        DataSourceStatus status,
        long version,
        int contentVersion,
        int publishedContentVersion,
        Instant createdAt) {

    public TextDataSourceSummary {
        Objects.requireNonNull(id, "数据源标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(name, "数据源名称不能为空");
        Objects.requireNonNull(status, "数据源状态不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
