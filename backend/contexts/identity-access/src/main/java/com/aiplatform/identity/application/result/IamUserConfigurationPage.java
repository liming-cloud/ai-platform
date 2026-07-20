package com.aiplatform.identity.application.result;

import java.util.List;
import java.util.Objects;

/**
 * 租户用户目录的稳定分页结果；数据项只能来自同一租户范围的数据库分页查询。
 */
public record IamUserConfigurationPage(
        List<IamUserConfigurationResult> items,
        long total,
        int page,
        int pageSize
) {
    public IamUserConfigurationPage {
        items = List.copyOf(Objects.requireNonNull(items, "用户分页数据不能为空"));
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
        if (total < 0 || items.size() > pageSize || items.size() > total) {
            throw new IllegalArgumentException("用户分页统计不合法");
        }
        if (total > 0 && page > totalPages(total, pageSize)) {
            throw new IllegalArgumentException("用户分页页码超出总页数");
        }
    }

    public int totalPages() {
        return totalPages(total, pageSize);
    }

    private static int totalPages(long total, int pageSize) {
        return total == 0 ? 0 : Math.toIntExact(Math.ceilDiv(total, (long) pageSize));
    }
}
