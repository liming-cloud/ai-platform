package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** 平台范围探测输入；平台凭据引用不能由租户解析器读取。 */
public record PlatformProviderProbeRequest(
        ProviderKind providerKind, URI endpoint, CredentialReference credentialReference,
        String modelCode, Duration timeout) {
    public PlatformProviderProbeRequest {
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        Objects.requireNonNull(credentialReference, "平台凭据引用不能为空");
        Objects.requireNonNull(modelCode, "模型编码不能为空");
        Objects.requireNonNull(timeout, "探测超时不能为空");
    }
}
