package com.aiplatform.foundation.trustedquery.domain.query.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 查询领域冻结的模型令牌用量，不依赖任何模型厂商或运行框架类型。
 */
public record QueryModelUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens) implements ValueObject {

    public static final QueryModelUsage UNKNOWN = new QueryModelUsage(0, 0, 0);

    public QueryModelUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("查询模型用量不能为负数");
        }
        if (totalTokens > 0 && totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("模型总用量不能小于输入与输出用量之和");
        }
    }
}
