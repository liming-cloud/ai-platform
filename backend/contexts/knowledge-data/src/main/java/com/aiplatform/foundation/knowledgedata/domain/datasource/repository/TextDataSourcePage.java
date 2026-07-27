package com.aiplatform.foundation.knowledgedata.domain.datasource.repository;

import java.util.List;
import java.util.Objects;

/**
 * 文本数据源仓储返回的领域分页事实。
 */
public record TextDataSourcePage(
        List<TextDataSourceSummary> items,
        long pageNumber,
        long pageSize,
        long totalElements) {

    public TextDataSourcePage {
        Objects.requireNonNull(items, "文本数据源列表不能为空");
        items = List.copyOf(items);
        if (pageNumber < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页参数必须是正整数");
        }
        if (totalElements < items.size()) {
            throw new IllegalArgumentException("数据源总数不能小于当前页数量");
        }
    }

    public long totalPages() {
        return totalElements == 0 ? 0 : (totalElements + pageSize - 1) / pageSize;
    }

    public boolean hasNext() {
        return pageNumber < totalPages();
    }
}
