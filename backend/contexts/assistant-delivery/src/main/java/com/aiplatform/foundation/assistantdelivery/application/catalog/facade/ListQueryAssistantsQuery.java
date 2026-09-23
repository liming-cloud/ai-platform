package com.aiplatform.foundation.assistantdelivery.application.catalog.facade;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 按页码读取查询渠道助手目录。
 */
public record ListQueryAssistantsQuery(
        TrustedRequestContext context,
        PageQuery page) {

    public ListQueryAssistantsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
