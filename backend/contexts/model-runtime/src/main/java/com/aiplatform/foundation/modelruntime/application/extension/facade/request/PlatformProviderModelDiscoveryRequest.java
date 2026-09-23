package com.aiplatform.foundation.modelruntime.application.extension.facade.request;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 平台模型发现扩展输入；平台凭据与独立发现凭据均为不透明引用。 */
public record PlatformProviderModelDiscoveryRequest(ProviderKind providerKind,
        PlatformModelDiscoveryProfile discoveryProfile, URI endpoint, String region,
        String credentialReference, Optional<PlatformProviderDiscoveryCredentialReferences> discoveryCredentials,
        Duration timeout) {
    public PlatformProviderModelDiscoveryRequest {
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(discoveryProfile, "发现协议不能为空");
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        Objects.requireNonNull(region, "区域不能为空");
        if (credentialReference == null || credentialReference.isBlank()) {
            throw new IllegalArgumentException("平台凭据引用不能为空");
        }
        discoveryCredentials = Objects.requireNonNull(discoveryCredentials, "发现凭据引用不能为空");
        if ((discoveryProfile == PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE)
                != discoveryCredentials.isPresent()) {
            throw new IllegalArgumentException("火山方舟控制面发现必须且只能使用独立 AK/SK 引用");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("发现超时必须大于零");
        }
    }
}
