package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import java.util.List;
import java.util.Objects;

/**
 * 助手仓储分页结果。
 */
public record AssistantPage(List<Assistant> items, long totalElements) {

    public AssistantPage {
        Objects.requireNonNull(items, "助手列表不能为空");
        items = List.copyOf(items);
        if (totalElements < 0L) {
            throw new IllegalArgumentException("助手总数不能为负数");
        }
    }
}
