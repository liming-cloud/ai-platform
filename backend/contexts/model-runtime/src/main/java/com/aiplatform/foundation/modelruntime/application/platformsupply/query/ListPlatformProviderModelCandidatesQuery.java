package com.aiplatform.foundation.modelruntime.application.platformsupply.query;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/** 平台连接当前修订的模型候选分页查询。 */
public record ListPlatformProviderModelCandidatesQuery(
        PlatformRequestContext context,
        String providerConnectionId,
        long expectedConnectionVersion,
        PageQuery page) {
    public ListPlatformProviderModelCandidatesQuery {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        providerConnectionId = Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        if (expectedConnectionVersion < 0) throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        page = Objects.requireNonNull(page, "分页查询不能为空");
    }
}
