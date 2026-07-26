package com.aiplatform.foundation.knowledgedata.api.model;

import java.util.List;
import java.util.Objects;

/**
 * 索引版本游标分页结果。
 */
public record IndexVersionPage(
        List<IndexVersionView> items,
        String nextCursor) {

    public IndexVersionPage {
        Objects.requireNonNull(items, "索引版本列表不能为空");
        items = List.copyOf(items);
    }
}
