package com.aiplatform.foundation.assistantdelivery.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 按不透明游标读取查询渠道助手目录。
 */
public record ListQueryAssistantsQuery(
        TrustedRequestContext context,
        String cursor,
        int limit) {

    public ListQueryAssistantsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
    }
}
