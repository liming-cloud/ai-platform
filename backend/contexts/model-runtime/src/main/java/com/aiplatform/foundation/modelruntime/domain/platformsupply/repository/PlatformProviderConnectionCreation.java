package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import java.util.Objects;

/** 平台供应商连接的幂等创建输入。 */
public record PlatformProviderConnectionCreation(
        PlatformProviderConnection connection,
        String idempotencyKey,
        String requestFingerprint) {

    public PlatformProviderConnectionCreation {
        Objects.requireNonNull(connection, "平台供应商连接不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
    }
}
