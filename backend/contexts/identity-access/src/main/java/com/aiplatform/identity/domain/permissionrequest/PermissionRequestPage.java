package com.aiplatform.identity.domain.permissionrequest;

import java.util.List;
import java.util.Objects;

/**
 * 权限申请数据库分页结果；排序和筛选必须在仓储查询中完成。
 */
public record PermissionRequestPage(
        List<PermissionRequest> items,
        long total,
        int page,
        int pageSize
) {
    public PermissionRequestPage {
        items = List.copyOf(Objects.requireNonNull(items, "权限申请分页数据不能为空"));
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
        if (total < 0 || items.size() > pageSize || items.size() > total) {
            throw new IllegalArgumentException("权限申请分页计数不合法");
        }
    }

    public int totalPages() {
        return total == 0 ? 0 : Math.toIntExact((total + pageSize - 1) / pageSize);
    }
}
