package com.aiplatform.foundation.modelruntime.application.provider.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 在可信租户上下文中分页查询供应商连接。
 */
public record ListProviderConnectionsQuery(
        TrustedRequestContext context,
        PageQuery page) {

    public ListProviderConnectionsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
