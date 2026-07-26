package com.aiplatform.foundation.assistantdelivery.application.port.out;

import java.util.List;
import java.util.Objects;

/**
 * 查询渠道已发布助手投影的游标切片。
 */
public record PublishedAssistantSlice(
        List<PublishedAssistantSummary> items,
        String nextCursor) {

    public PublishedAssistantSlice {
        items = List.copyOf(Objects.requireNonNull(items, "已发布助手列表不能为空"));
    }
}
