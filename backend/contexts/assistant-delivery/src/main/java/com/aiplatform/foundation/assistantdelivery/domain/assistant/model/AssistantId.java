package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 租户内助手身份标识。
 */
public record AssistantId(long value) implements ValueObject {

    public AssistantId {
        if (value <= 0) {
            throw new IllegalArgumentException("助手标识必须是正整数");
        }
    }

    public static AssistantId parse(String value) {
        try {
            return new AssistantId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("助手标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
