package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 从指定序号之后读取查询事件。
 */
public record ListQueryRunEventsQuery(
        TrustedRequestContext context,
        String queryRunId,
        long afterSequence,
        int limit) {

    public ListQueryRunEventsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        if (afterSequence < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("查询事件游标或数量不正确");
        }
    }
}
