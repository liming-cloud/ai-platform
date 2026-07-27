package com.aiplatform.foundation.assistantdelivery.api.model.assistant;

import java.util.Objects;

/**
 * 助手创建结果及幂等重放事实。
 */
public record CreateAssistantResult(AssistantView assistant, boolean replayed) {

    public CreateAssistantResult {
        Objects.requireNonNull(assistant, "助手视图不能为空");
    }
}
