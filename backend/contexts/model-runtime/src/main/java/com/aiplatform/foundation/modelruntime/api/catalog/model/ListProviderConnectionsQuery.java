package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;

/**
 * 在可信租户上下文中分页查询供应商连接。
 */
public record ListProviderConnectionsQuery(
        TrustedRequestContext context,
        String cursor,
        int limit) {

    private static final int MAX_CURSOR_LENGTH = 512;

    public ListProviderConnectionsQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (cursor != null
                && (cursor.isBlank()
                || !cursor.equals(cursor.trim())
                || cursor.length() > MAX_CURSOR_LENGTH)) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
    }
}
