package com.aiplatform.foundation.shared.contract.paging;

import java.util.List;
import java.util.Objects;

/**
 * 包含总数和页状态的不可变分页结果。
 *
 * @param <T> 条目类型
 */
public record PageResult<T>(
        List<T> items,
        long pageNumber,
        long pageSize,
        long totalElements,
        long totalPages,
        boolean hasPrevious,
        boolean hasNext) {

    public PageResult {
        items = List.copyOf(Objects.requireNonNull(items, "分页条目不能为空"));
        if (pageNumber < 1L || pageSize < 1L || pageSize > PageQuery.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        if (totalElements < 0L || totalPages < 0L) {
            throw new IllegalArgumentException("分页总数不能为负数");
        }
        if (items.size() > pageSize) {
            throw new IllegalArgumentException("分页条目超过每页数量");
        }
        var expectedPages = pages(totalElements, pageSize);
        if (totalPages != expectedPages
                || hasPrevious != (totalElements > 0L && pageNumber > 1L)
                || hasNext != (pageNumber < expectedPages)) {
            throw new IllegalArgumentException("分页派生状态不一致");
        }
    }

    public static <T> PageResult<T> of(
            List<T> items,
            PageQuery query,
            long totalElements) {
        Objects.requireNonNull(query, "分页查询不能为空");
        var totalPages = pages(totalElements, query.pageSize());
        return new PageResult<>(
                items,
                query.pageNumber(),
                query.pageSize(),
                totalElements,
                totalPages,
                totalElements > 0L && query.pageNumber() > 1L,
                query.pageNumber() < totalPages);
    }

    private static long pages(long totalElements, long pageSize) {
        if (totalElements < 0L) {
            throw new IllegalArgumentException("分页总数不能为负数");
        }
        return totalElements == 0L
                ? 0L
                : 1L + (totalElements - 1L) / pageSize;
    }
}
