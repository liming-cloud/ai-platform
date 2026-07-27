package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

import java.util.Objects;

/**
 * 文本数据源发布或幂等重放结果。
 */
public record PublishTextDataSourceResult(
        TextDataSourceView dataSource,
        boolean replayed) {

    public PublishTextDataSourceResult {
        Objects.requireNonNull(dataSource, "文本数据源不能为空");
    }
}
