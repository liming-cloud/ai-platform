package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import java.util.Objects;

/**
 * 助手创建或幂等重放结果。
 */
public record AssistantCreationOutcome(Assistant assistant, boolean replayed) {

    public AssistantCreationOutcome {
        Objects.requireNonNull(assistant, "助手不能为空");
    }
}
