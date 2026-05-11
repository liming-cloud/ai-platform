package org.fujay.ai.platform.model.application.command;

import org.fujay.ai.platform.model.domain.value.ProviderType;

public record SavePlatformProviderTypeCommand(
        Long providerTypeId,
        ProviderType providerType,
        String displayName,
        String defaultBaseUrl,
        String status,
        Integer sortOrder) {
}
