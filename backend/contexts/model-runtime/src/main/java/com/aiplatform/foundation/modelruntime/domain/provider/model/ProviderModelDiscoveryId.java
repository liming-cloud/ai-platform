package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 厂商连接模型发现事实的稳定标识。
 */
public record ProviderModelDiscoveryId(long value) implements ValueObject {

    public ProviderModelDiscoveryId {
        if (value <= 0) {
            throw new IllegalArgumentException("模型发现标识必须是正整数");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
