package com.aiplatform.foundation.knowledgedata.application.datasource.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/**
 * 查询知识空间内文本数据源的条件。
 */
public record ListTextDataSourcesQuery(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        PageQuery page) {

    public ListTextDataSourcesQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
    }
}
