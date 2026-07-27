package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;

/**
 * 按可信租户读取查询运行。
 */
public record GetQueryRunQuery(
        TrustedRequestContext context,
        String queryRunId) {

    public GetQueryRunQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
    }
}
