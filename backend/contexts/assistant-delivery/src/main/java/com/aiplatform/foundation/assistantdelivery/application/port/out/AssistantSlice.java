package com.aiplatform.foundation.assistantdelivery.application.port.out;

import com.aiplatform.foundation.assistantdelivery.domain.Assistant;
import java.util.List;
import java.util.Objects;

/**
 * 助手仓储游标切片。
 */
public record AssistantSlice(List<Assistant> items, String nextCursor) {

    public AssistantSlice {
        Objects.requireNonNull(items, "助手列表不能为空");
        items = List.copyOf(items);
    }
}
