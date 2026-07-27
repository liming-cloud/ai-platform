package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 助手领域内记录的创建主体标识。
 */
public record CreatorId(long value) implements ValueObject {

    public CreatorId {
        if (value <= 0L) {
            throw new IllegalArgumentException("创建主体标识必须是正整数");
        }
    }
}
