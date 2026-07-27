package com.aiplatform.foundation.knowledgedata.api.model.datasource;

import java.util.List;
import java.util.Objects;

/**
 * 文本数据源游标分页结果。
 */
public record TextDataSourcePage(
        List<TextDataSourceView> items,
        String nextCursor) {

    public TextDataSourcePage {
        items = List.copyOf(Objects.requireNonNull(items, "数据源列表不能为空"));
    }
}
