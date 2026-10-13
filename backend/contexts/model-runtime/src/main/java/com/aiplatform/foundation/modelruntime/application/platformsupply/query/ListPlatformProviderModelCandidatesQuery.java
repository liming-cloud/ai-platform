package com.aiplatform.foundation.modelruntime.application.platformsupply.query;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/** 平台连接当前修订的模型候选分页查询。 */
public record ListPlatformProviderModelCandidatesQuery(
        PlatformRequestContext context,
        String providerConnectionId,
        long expectedConnectionVersion,
        PageQuery page,
        String q) {
    /** @throws IllegalArgumentException 修订为负数，或搜索词超过六十四个 Unicode 码点。 */
    public ListPlatformProviderModelCandidatesQuery {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        providerConnectionId = Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        if (expectedConnectionVersion < 0) throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        q = q == null ? "" : q.strip();
        if (q.codePointCount(0, q.length()) > 64) throw new IllegalArgumentException("目录搜索词不能超过六十四个字符");
    }
}
