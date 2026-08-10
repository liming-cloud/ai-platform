package com.aiplatform.foundation.modelruntime.application.provider.dto;

import java.util.Objects;

/**
 * 凭据轮换后的无秘密连接摘要。
 */
public record RotateProviderConnectionCredentialResult(ProviderConnectionView providerConnection) {

    public RotateProviderConnectionCredentialResult {
        Objects.requireNonNull(providerConnection, "供应商连接摘要不能为空");
    }
}
