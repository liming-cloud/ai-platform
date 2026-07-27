package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

import java.util.Objects;

/**
 * 文本数据源创建或幂等重放结果。
 */
public record CreateTextDataSourceResult(
        TextDataSourceView dataSource,
        boolean replayed) {

    public CreateTextDataSourceResult {
        Objects.requireNonNull(dataSource, "文本数据源不能为空");
    }
}
