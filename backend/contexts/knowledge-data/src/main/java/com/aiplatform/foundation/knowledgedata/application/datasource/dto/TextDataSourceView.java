package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * 面向公共接口的文本数据源视图。
 */
public record TextDataSourceView(
        String id,
        String knowledgeSpaceId,
        String name,
        String status,
        long version,
        int contentVersion,
        int publishedContentVersion,
        Instant createdAt) {

    public TextDataSourceView {
        Objects.requireNonNull(id, "数据源标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(name, "数据源名称不能为空");
        Objects.requireNonNull(status, "数据源状态不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
