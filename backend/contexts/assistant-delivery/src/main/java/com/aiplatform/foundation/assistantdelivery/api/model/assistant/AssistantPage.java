package com.aiplatform.foundation.assistantdelivery.api.model.assistant;

import java.util.List;
import java.util.Objects;

/**
 * 助手游标分页结果。
 */
public record AssistantPage(List<AssistantView> items, String nextCursor) {

    public AssistantPage {
        Objects.requireNonNull(items, "助手列表不能为空");
        items = List.copyOf(items);
    }
}
