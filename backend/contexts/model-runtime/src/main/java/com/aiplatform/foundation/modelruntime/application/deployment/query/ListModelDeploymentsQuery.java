package com.aiplatform.foundation.modelruntime.application.deployment.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 查询当前租户模型部署。
 */
public record ListModelDeploymentsQuery(
        TrustedRequestContext context,
        PageQuery page) {

    public ListModelDeploymentsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
