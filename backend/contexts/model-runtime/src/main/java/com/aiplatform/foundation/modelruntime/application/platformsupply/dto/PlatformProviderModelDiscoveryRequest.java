package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** 平台范围模型发现扩展的受控输入；不携带租户身份。 */
public record PlatformProviderModelDiscoveryRequest(
        ProviderKind providerKind,
        URI endpoint,
        CredentialReference credentialReference,
        Duration timeout) {

    public PlatformProviderModelDiscoveryRequest {
        providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        endpoint = Objects.requireNonNull(endpoint, "供应商地址不能为空");
        credentialReference = Objects.requireNonNull(credentialReference, "凭据引用不能为空");
        timeout = Objects.requireNonNull(timeout, "模型发现超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("模型发现超时必须大于零");
        }
    }
}
