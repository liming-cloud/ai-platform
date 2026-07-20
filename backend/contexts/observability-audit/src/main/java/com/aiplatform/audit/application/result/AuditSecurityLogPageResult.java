package com.aiplatform.audit.application.result;

import java.util.List;
import java.util.Objects;

/**
 * 安全审计数据库分页结果，不向应用层泄漏 HTTP 响应类型。
 */
public record AuditSecurityLogPageResult(
        List<AuditSecurityLogResult> items,
        int page,
        int pageSize,
        long total
) {
    public AuditSecurityLogPageResult {
        items = List.copyOf(Objects.requireNonNull(items, "分页数据不能为空"));
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页条数必须为 1 到 100");
        }
        if (total < 0) {
            throw new IllegalArgumentException("总条数不能小于 0");
        }
    }

    public int totalPages() {
        return total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
    }
}
