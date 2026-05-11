package org.fujay.ai.platform.model.interfaces.request;

public record SavePlatformProviderTypeRequest(
        String providerTypeId,
        String providerType,
        String displayName,
        String defaultBaseUrl,
        String status,
        Integer sortOrder) {
}
