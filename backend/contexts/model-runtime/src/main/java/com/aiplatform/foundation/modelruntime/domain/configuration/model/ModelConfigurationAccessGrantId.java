package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 模型配置授权的稳定标识。
 */
public record ModelConfigurationAccessGrantId(long value) implements ValueObject {

    public ModelConfigurationAccessGrantId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型配置授权标识必须是正整数");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
