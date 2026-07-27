package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;

/**
 * 查询知识空间内文本数据源的条件。
 */
public record ListTextDataSourcesQuery(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String cursor,
        int limit) {

    public ListTextDataSourcesQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
    }
}
