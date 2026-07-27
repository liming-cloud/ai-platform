package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common;

/**
 * 一次模型调用的标准化令牌用量。
 */
public record ModelUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens) {

    public static final ModelUsage UNKNOWN = new ModelUsage(0, 0, 0);

    public ModelUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("模型用量不能为负数");
        }
        if (totalTokens > 0 && totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("模型总用量不能小于输入与输出用量之和");
        }
    }
}
