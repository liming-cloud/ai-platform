package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;

/** 创建平台供应商连接的 HTTP 输入；方舟控制面 AK/SK 仅允许在创建时提交。 */
public record PlatformProviderConnectionRequest(
        ProviderKind providerKind,
        PlatformModelDiscoveryProfile discoveryProfile,
        String displayName,
        URI endpoint,
        String region,
        AuthenticationType authenticationType,
        String apiKey,
        String discoveryAccessKeyId,
        String discoverySecretAccessKey) {
}
