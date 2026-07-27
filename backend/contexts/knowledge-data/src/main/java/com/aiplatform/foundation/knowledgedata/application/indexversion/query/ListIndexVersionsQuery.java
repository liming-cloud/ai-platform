package com.aiplatform.foundation.knowledgedata.application.indexversion.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 在可信租户内按知识空间列出索引版本的查询。
 */
public record ListIndexVersionsQuery(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        PageQuery page) {

    public ListIndexVersionsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
