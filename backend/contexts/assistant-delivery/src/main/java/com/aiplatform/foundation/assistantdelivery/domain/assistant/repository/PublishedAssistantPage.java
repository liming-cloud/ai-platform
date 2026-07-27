package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import java.util.List;
import java.util.Objects;

/**
 * 查询渠道已发布助手投影的分页结果。
 */
public record PublishedAssistantPage(
        List<PublishedAssistantSummary> items,
        long totalElements) {

    public PublishedAssistantPage {
        items = List.copyOf(Objects.requireNonNull(items, "已发布助手列表不能为空"));
        if (totalElements < 0L) {
            throw new IllegalArgumentException("已发布助手总数不能为负数");
        }
    }
}
