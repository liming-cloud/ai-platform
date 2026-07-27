package com.aiplatform.foundation.knowledgedata.application.port.out.datasource;

import java.util.List;
import java.util.Objects;

/**
 * 文本数据源持久化游标切片。
 */
public record TextDataSourceSlice(
        List<TextDataSourceSummary> items,
        String nextCursor) {

    public TextDataSourceSlice {
        items = List.copyOf(Objects.requireNonNull(items, "数据源切片不能为空"));
    }
}
