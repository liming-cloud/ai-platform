package com.aiplatform.foundation.modelruntime.application.provider.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/** 在可信租户范围分页读取指定供应商连接的模型发现历史。 */
public record ListProviderModelDiscoveriesQuery(
        TrustedRequestContext context,
        String providerConnectionId,
        PageQuery page) {

    public ListProviderModelDiscoveriesQuery {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        providerConnectionId = Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (providerConnectionId.isBlank()) {
            throw new IllegalArgumentException("供应商连接标识不能为空");
        }
        page = Objects.requireNonNull(page, "分页参数不能为空");
    }
}
