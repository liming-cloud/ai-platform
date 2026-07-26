package com.aiplatform.foundation.assistantdelivery.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 租户内助手身份标识。
 */
public record AssistantId(UUID value) {

    public AssistantId {
        Objects.requireNonNull(value, "助手标识不能为空");
    }
}
