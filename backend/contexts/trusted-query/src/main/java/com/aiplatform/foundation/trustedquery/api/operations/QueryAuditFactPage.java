package com.aiplatform.foundation.trustedquery.api.operations;

import java.util.List;
import java.util.Objects;

/**
 * 脱敏查询事实的稳定游标分页结果。
 */
public record QueryAuditFactPage(
        List<QueryAuditFact> items,
        String nextCursor) {

    public QueryAuditFactPage {
        items = List.copyOf(Objects.requireNonNull(items, "查询审计事实不能为空"));
    }
}
