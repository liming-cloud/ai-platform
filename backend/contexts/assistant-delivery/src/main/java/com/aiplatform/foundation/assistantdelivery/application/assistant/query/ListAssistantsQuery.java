package com.aiplatform.foundation.assistantdelivery.application.assistant.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 租户内助手页码分页查询。
 */
public record ListAssistantsQuery(
        TrustedRequestContext context,
        PageQuery page) {

    public ListAssistantsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
