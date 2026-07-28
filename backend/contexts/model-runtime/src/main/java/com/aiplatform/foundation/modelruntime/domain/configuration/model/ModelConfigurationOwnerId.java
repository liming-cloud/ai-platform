package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 个人模型配置所有者在模型上下文内的稳定标识。
 */
public record ModelConfigurationOwnerId(long value) implements ValueObject {

    public ModelConfigurationOwnerId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型配置所有者标识必须是正整数");
        }
    }
}
