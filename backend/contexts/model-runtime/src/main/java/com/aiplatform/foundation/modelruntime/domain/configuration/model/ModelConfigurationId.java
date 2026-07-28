package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 模型配置的稳定标识。
 */
public record ModelConfigurationId(long value) implements ValueObject {

    public ModelConfigurationId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型配置标识必须是正整数");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
