package com.aiplatform.foundation.assistantdelivery.api.model;

import java.util.List;
import java.util.Objects;

/**
 * 查询渠道助手目录的游标分页结果。
 */
public record QueryAssistantPage(
        List<QueryAssistantView> items,
        String nextCursor) {

    public QueryAssistantPage {
        items = List.copyOf(Objects.requireNonNull(items, "查询助手列表不能为空"));
    }
}
