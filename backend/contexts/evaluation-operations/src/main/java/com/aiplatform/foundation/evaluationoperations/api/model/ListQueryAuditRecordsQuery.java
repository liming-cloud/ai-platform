package com.aiplatform.foundation.evaluationoperations.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 请求当前租户的脱敏查询审计事实。
 */
public record ListQueryAuditRecordsQuery(
        TrustedRequestContext context,
        String cursor,
        int limit) {

    public ListQueryAuditRecordsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
    }
}
