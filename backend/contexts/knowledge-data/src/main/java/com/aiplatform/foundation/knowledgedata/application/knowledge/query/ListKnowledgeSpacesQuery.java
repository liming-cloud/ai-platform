package com.aiplatform.foundation.knowledgedata.application.knowledge.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 在可信租户上下文中分页查询知识空间。
 */
public record ListKnowledgeSpacesQuery(
        TrustedRequestContext context,
        PageQuery page) {

    public ListKnowledgeSpacesQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
