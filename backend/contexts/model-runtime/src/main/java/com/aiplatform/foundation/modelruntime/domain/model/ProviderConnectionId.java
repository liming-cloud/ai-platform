package com.aiplatform.foundation.modelruntime.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 租户内供应商连接的稳定标识。
 */
public record ProviderConnectionId(UUID value) {

    public ProviderConnectionId {
        Objects.requireNonNull(value, "供应商连接标识不能为空");
    }
}
