package com.aiplatform.foundation.assistantdelivery.application.answer.facade;

/**
 * 本次可信回答的模型令牌用量。
 */
public record AssistantTrustedAnswerUsage(
        long inputTokens,
        long outputTokens,
        long totalTokens) {

    public AssistantTrustedAnswerUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0
                || (totalTokens > 0 && totalTokens < inputTokens + outputTokens)) {
            throw new IllegalArgumentException("可信回答模型用量不正确");
        }
    }
}
