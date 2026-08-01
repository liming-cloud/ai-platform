package com.aiplatform.foundation.trustedquery.application.diagnostic.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 当前可信主体读取单个查询诊断的请求。
 */
public record GetQueryDiagnosticQuery(
        TrustedRequestContext context,
        String queryRunId) {

    public GetQueryDiagnosticQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (queryRunId == null || queryRunId.isBlank()) {
            throw new IllegalArgumentException("查询运行标识不能为空");
        }
    }
}
