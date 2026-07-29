package com.aiplatform.foundation.knowledgedata.application.datasource.query;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/** 知识管理员读取 FILE 来源摘要的可信分页请求。 */
public record ListGovernedFileDataSourcesQuery(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        PageQuery page) {

    public ListGovernedFileDataSourcesQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(page, "分页参数不能为空");
    }
}
