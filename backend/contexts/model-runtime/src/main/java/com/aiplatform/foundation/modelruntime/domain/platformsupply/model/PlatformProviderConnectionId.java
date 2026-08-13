package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 平台范围供应商连接的稳定标识。 */
public record PlatformProviderConnectionId(long value) implements ValueObject {

    public PlatformProviderConnectionId {
        if (value <= 0) {
            throw new IllegalArgumentException("平台供应商连接标识必须是正整数");
        }
    }

    public static PlatformProviderConnectionId parse(String value) {
        try {
            return new PlatformProviderConnectionId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("平台供应商连接标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
