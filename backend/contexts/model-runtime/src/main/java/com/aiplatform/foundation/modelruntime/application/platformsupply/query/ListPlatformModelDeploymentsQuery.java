package com.aiplatform.foundation.modelruntime.application.platformsupply.query;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/** 只按公开部署名称与驻留摘要读取标准分页，不接受其他字段搜索。 */
public record ListPlatformModelDeploymentsQuery(PlatformRequestContext context, PageQuery page, String q) {
    /** @throws IllegalArgumentException 搜索词超过六十四个 Unicode 码点。 */
    public ListPlatformModelDeploymentsQuery {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        page = Objects.requireNonNull(page, "分页参数不能为空");
        q = q == null ? "" : q.strip();
        if (q.codePointCount(0, q.length()) > 64) throw new IllegalArgumentException("目录搜索词不能超过六十四个字符");
    }
}
