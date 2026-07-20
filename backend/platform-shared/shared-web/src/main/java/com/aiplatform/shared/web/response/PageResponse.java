package com.aiplatform.shared.web.response;

import java.util.List;
import java.util.Objects;

/**
 * Stable HTTP pagination contract. Filtering and sorting must happen before this response is built.
 */
public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int totalPages) {
    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "分页数据不能为空"));
        if (page < 1) throw new IllegalArgumentException("页码必须大于 0");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("每页条数必须为 1 到 100");
        if (total < 0) throw new IllegalArgumentException("总条数不能小于 0");
        int expectedPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        if (totalPages != expectedPages) throw new IllegalArgumentException("总页数与总条数不一致");
    }

    public static <T> PageResponse<T> from(List<T> source, int page, int pageSize) {
        List<T> rows = List.copyOf(Objects.requireNonNull(source, "分页源数据不能为空"));
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.min(100, Math.max(1, pageSize));
        int from = Math.min(rows.size(), (normalizedPage - 1) * normalizedPageSize);
        int to = Math.min(rows.size(), from + normalizedPageSize);
        int pages = rows.isEmpty() ? 0 : (int) Math.ceil((double) rows.size() / normalizedPageSize);
        return new PageResponse<>(rows.subList(from, to), normalizedPage, normalizedPageSize, rows.size(), pages);
    }
}
