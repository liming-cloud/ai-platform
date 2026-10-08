package com.aiplatform.foundation.assistantdelivery.application.catalog.facade;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * 按页码读取查询渠道助手目录。
 */
public record ListQueryAssistantsQuery(
        TrustedRequestContext context,
        PageQuery page, String q, Set<Long> assistantIds, boolean allPublished) {

    public ListQueryAssistantsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
        q = Objects.requireNonNull(q, "搜索词不能为空").strip();
        assistantIds = Set.copyOf(assistantIds);
        if (q.length() > 160 || assistantIds.stream().anyMatch(id -> id < 1)
                || (allPublished && !assistantIds.isEmpty())) throw new IllegalArgumentException("助手目录范围不合法");
    }
}
