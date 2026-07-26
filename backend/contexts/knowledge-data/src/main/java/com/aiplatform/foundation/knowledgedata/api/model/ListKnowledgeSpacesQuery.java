package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;

/**
 * 在可信租户上下文中分页查询知识空间。
 */
public record ListKnowledgeSpacesQuery(
        TrustedRequestContext context,
        String cursor,
        int limit) {

    private static final int MAX_CURSOR_LENGTH = 512;
    private static final int MAX_LIMIT = 100;

    public ListKnowledgeSpacesQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (cursor != null
                && (cursor.isBlank()
                || cursor.length() > MAX_CURSOR_LENGTH
                || !cursor.equals(cursor.trim()))) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
    }
}
