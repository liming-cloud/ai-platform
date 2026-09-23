package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 平台范围模型发现扩展的受控输入；不携带租户身份。 */
public record PlatformProviderModelDiscoveryRequest(
        ProviderKind providerKind,
        PlatformModelDiscoveryProfile discoveryProfile,
        URI endpoint,
        String region,
        CredentialReference credentialReference,
        Optional<PlatformProviderDiscoveryCredentialReferences> discoveryCredentials,
        Duration timeout) {

    public PlatformProviderModelDiscoveryRequest {
        providerKind = Objects.requireNonNull(providerKind, "供应商类型不能为空");
        discoveryProfile = Objects.requireNonNull(discoveryProfile, "模型发现协议不能为空");
        endpoint = Objects.requireNonNull(endpoint, "供应商地址不能为空");
        region = Objects.requireNonNull(region, "供应商区域不能为空");
        credentialReference = Objects.requireNonNull(credentialReference, "凭据引用不能为空");
        discoveryCredentials = Objects.requireNonNull(discoveryCredentials, "发现凭据引用不能为空");
        timeout = Objects.requireNonNull(timeout, "模型发现超时不能为空");
        if ((discoveryProfile == PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE)
                != discoveryCredentials.isPresent()) {
            throw new IllegalArgumentException("火山方舟控制面发现必须且只能使用独立 AK/SK 引用");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("模型发现超时必须大于零");
        }
    }

    /** 仅供历史调用方迁移；新连接必须显式冻结发现协议。 */
    public PlatformProviderModelDiscoveryRequest(
            ProviderKind providerKind,
            PlatformModelDiscoveryProfile discoveryProfile,
            URI endpoint,
            CredentialReference credentialReference,
            Duration timeout) {
        this(providerKind, discoveryProfile, endpoint, "global", credentialReference, Optional.empty(), timeout);
    }

    /** 仅供历史调用方迁移；新连接必须显式冻结发现协议。 */
    public PlatformProviderModelDiscoveryRequest(
            ProviderKind providerKind,
            URI endpoint,
            CredentialReference credentialReference,
            Duration timeout) {
        this(providerKind, PlatformModelDiscoveryProfile.legacyDefault(providerKind), endpoint,
                "global", credentialReference, Optional.empty(), timeout);
    }

    /** 返回扩展契约供应商类型。 */
    public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKindContract() {
        return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.valueOf(providerKind.name());
    }

    /** 返回扩展契约发现协议。 */
    public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile discoveryProfileContract() {
        return com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile.valueOf(discoveryProfile.name());
    }
}
