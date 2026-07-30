package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 助手草稿测试运行的稳定标识。
 */
public record AssistantTestRunId(long value) implements ValueObject {

    public AssistantTestRunId {
        if (value <= 0) {
            throw new IllegalArgumentException("助手测试运行标识必须是正整数");
        }
    }
}
