package com.aiplatform.foundation.assistantdelivery.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 不可变助手版本标识。
 */
public record AssistantVersionId(UUID value) {

    public AssistantVersionId {
        Objects.requireNonNull(value, "助手版本标识不能为空");
    }
}
