package com.aiplatform.foundation.modelruntime.domain.model;

/**
 * 租户内供应商连接的稳定标识。
 */
public record ProviderConnectionId(long value) {

    public ProviderConnectionId {
        if (value <= 0) {
            throw new IllegalArgumentException("供应商连接标识必须是正整数");
        }
    }

    public static ProviderConnectionId parse(String value) {
        try {
            return new ProviderConnectionId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("供应商连接标识格式不正确", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
