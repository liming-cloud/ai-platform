package org.fujay.ai.platform.model.application.dto;

import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;

public record ModelDto(
        Long modelId,
        Long providerId,
        String modelCode,
        String displayName,
        ModelCapabilityType capabilityType,
        String status,
        ModelParameterDefaults defaults) {
}
