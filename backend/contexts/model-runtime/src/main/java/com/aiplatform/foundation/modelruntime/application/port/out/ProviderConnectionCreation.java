package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import java.util.Objects;

/**
 * 携带幂等事实的供应商连接创建请求。
 */
public record ProviderConnectionCreation(
        ProviderConnection connection,
        String idempotencyKey,
        String requestFingerprint) {

    public ProviderConnectionCreation {
        Objects.requireNonNull(connection, "供应商连接不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
    }
}
