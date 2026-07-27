package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 查询当前租户模型部署。
 */
public record ListModelDeploymentsQuery(
        TrustedRequestContext context,
        String cursor,
        int limit) {

    public ListModelDeploymentsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
    }
}
