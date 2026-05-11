package org.fujay.ai.platform.model.application.command;

import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;

public record SavePlatformModelOptionCommand(
        Long modelOptionId,
        Long providerTypeId,
        String modelCode,
        String displayName,
        ModelCapabilityType capabilityType,
        ModelParameterDefaults defaults,
        String status,
        Integer sortOrder) {
}
