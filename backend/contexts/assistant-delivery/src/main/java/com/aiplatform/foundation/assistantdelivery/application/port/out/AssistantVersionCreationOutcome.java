package com.aiplatform.foundation.assistantdelivery.application.port.out;

import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersion;
import java.util.Objects;

/**
 * 助手版本创建或幂等重放结果。
 */
public record AssistantVersionCreationOutcome(
        AssistantVersion assistantVersion,
        boolean replayed) {

    public AssistantVersionCreationOutcome {
        Objects.requireNonNull(assistantVersion, "助手版本不能为空");
    }
}
