package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import java.util.List;
import java.util.Objects;

/**
 * 索引版本游标分页切片。
 */
public record IndexVersionSlice(
        List<IndexVersion> items,
        String nextCursor) {

    public IndexVersionSlice {
        Objects.requireNonNull(items, "索引版本列表不能为空");
        items = List.copyOf(items);
    }
}
