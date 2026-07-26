package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import java.util.UUID;

/**
 * 按可信租户读取查询运行。
 */
public record GetQueryRunQuery(
        TrustedRequestContext context,
        UUID queryRunId) {

    public GetQueryRunQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
    }
}
