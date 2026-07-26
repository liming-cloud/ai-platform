package com.aiplatform.foundation.evaluationoperations.api.model;

import java.util.List;
import java.util.Objects;

/**
 * 查询审计视图的稳定游标分页结果。
 */
public record QueryAuditRecordPage(
        List<QueryAuditRecordView> items,
        String nextCursor) {

    public QueryAuditRecordPage {
        items = List.copyOf(Objects.requireNonNull(items, "查询审计记录不能为空"));
    }
}
