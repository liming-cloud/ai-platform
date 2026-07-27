package com.aiplatform.foundation.modelruntime.application.provider.dto;

import java.util.Objects;

/**
 * 供应商连接创建结果。
 */
public record CreateProviderConnectionResult(
        ProviderConnectionView providerConnection,
        boolean replayed) {

    public CreateProviderConnectionResult {
        Objects.requireNonNull(providerConnection, "供应商连接不能为空");
    }
}
