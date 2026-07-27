package com.aiplatform.foundation.assistantdelivery.domain;

/**
 * 不可变助手版本标识。
 */
public record AssistantVersionId(long value) {

    public AssistantVersionId {
        if (value <= 0) {
            throw new IllegalArgumentException("助手版本标识必须是正整数");
        }
    }

    public static AssistantVersionId parse(String value) {
        try {
            return new AssistantVersionId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("助手版本标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
