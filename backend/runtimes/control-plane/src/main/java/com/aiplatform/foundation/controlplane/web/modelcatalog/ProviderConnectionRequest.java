package com.aiplatform.foundation.controlplane.web.modelcatalog;

import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import java.net.URI;

/**
 * 创建供应商连接的 HTTP 请求。
 */
public record ProviderConnectionRequest(
        ProviderKind providerKind,
        String displayName,
        URI endpoint,
        String region,
        AuthenticationType authenticationType,
        String credentialReference) {
}
