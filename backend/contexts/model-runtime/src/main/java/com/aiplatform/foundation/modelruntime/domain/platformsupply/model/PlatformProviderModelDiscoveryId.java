package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 平台范围模型发现事实的稳定标识。 */
public record PlatformProviderModelDiscoveryId(long value) implements ValueObject {
    public PlatformProviderModelDiscoveryId {
        if (value <= 0) throw new IllegalArgumentException("平台模型发现标识必须是正整数");
    }
    @Override public String toString() { return Long.toString(value); }
}
