package com.aiplatform.foundation.knowledgedata.api.model.datasource;

import java.util.Objects;

/**
 * 文本内容登记或幂等重放结果。
 */
public record UploadTextContentResult(
        TextDataSourceView dataSource,
        boolean replayed) {

    public UploadTextContentResult {
        Objects.requireNonNull(dataSource, "文本数据源不能为空");
    }
}
