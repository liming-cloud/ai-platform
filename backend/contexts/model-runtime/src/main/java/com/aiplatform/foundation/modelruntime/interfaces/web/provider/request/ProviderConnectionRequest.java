package com.aiplatform.foundation.modelruntime.interfaces.web.provider.request;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import java.net.URI;

/**
 * 创建供应商连接的 HTTP 请求。
 */
public record ProviderConnectionRequest(
        ProviderKind providerKind,
        PlatformModelDiscoveryProfile discoveryProfile,
        String displayName,
        URI endpoint,
        String region,
        AuthenticationType authenticationType,
        String apiKey) {
}
