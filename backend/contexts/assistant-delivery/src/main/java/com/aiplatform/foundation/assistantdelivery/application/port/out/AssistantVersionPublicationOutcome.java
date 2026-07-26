package com.aiplatform.foundation.assistantdelivery.application.port.out;

import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersion;
import java.util.Objects;

/**
 * 助手版本发布或幂等重放结果。
 */
public record AssistantVersionPublicationOutcome(
        AssistantVersion assistantVersion,
        boolean replayed) {

    public AssistantVersionPublicationOutcome {
        Objects.requireNonNull(assistantVersion, "助手版本不能为空");
    }
}
