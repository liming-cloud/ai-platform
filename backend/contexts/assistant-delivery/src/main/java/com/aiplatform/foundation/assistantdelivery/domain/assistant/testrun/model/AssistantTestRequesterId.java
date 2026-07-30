package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 助手草稿测试发起成员的稳定引用。
 */
public record AssistantTestRequesterId(long value) implements ValueObject {

    public AssistantTestRequesterId {
        if (value <= 0) {
            throw new IllegalArgumentException("助手测试发起成员标识必须是正整数");
        }
    }
}
