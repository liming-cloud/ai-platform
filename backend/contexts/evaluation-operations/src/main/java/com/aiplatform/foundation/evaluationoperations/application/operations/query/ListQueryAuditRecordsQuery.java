package com.aiplatform.foundation.evaluationoperations.application.operations.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 请求当前租户的脱敏查询审计事实。
 */
public record ListQueryAuditRecordsQuery(
        TrustedRequestContext context,
        PageQuery page) {

    public ListQueryAuditRecordsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页请求不能为空");
    }
}
