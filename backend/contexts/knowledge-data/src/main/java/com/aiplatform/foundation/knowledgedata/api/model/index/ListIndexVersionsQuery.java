package com.aiplatform.foundation.knowledgedata.api.model.index;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 在可信租户内按知识空间列出索引版本的查询。
 */
public record ListIndexVersionsQuery(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String cursor,
        int limit) {

    public ListIndexVersionsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
    }
}
