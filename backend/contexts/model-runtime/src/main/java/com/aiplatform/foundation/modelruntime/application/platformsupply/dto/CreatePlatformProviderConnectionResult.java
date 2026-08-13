package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import java.util.Objects;

/** 平台供应商连接创建结果。 */
public record CreatePlatformProviderConnectionResult(
        PlatformProviderConnectionView providerConnection,
        boolean replayed) {

    public CreatePlatformProviderConnectionResult {
        Objects.requireNonNull(providerConnection, "平台供应商连接不能为空");
    }
}
