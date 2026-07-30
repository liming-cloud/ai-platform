package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 草稿测试终态保留的模型用量，不含模型请求或回答正文。
 */
public record AssistantTestUsage(int inputTokens, int outputTokens) implements ValueObject {

    public static final AssistantTestUsage UNKNOWN = new AssistantTestUsage(0, 0);

    public AssistantTestUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("助手测试模型用量不能为负数");
        }
    }

    /**
     * 返回用于配额和审计摘要的总令牌数。
     */
    public int totalTokens() {
        return Math.addExact(inputTokens, outputTokens);
    }
}
