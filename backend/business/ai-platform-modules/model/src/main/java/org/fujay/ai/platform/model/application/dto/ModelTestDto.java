package org.fujay.ai.platform.model.application.dto;

import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;

public record ModelTestDto(
        boolean success,
        Long providerId,
        Long modelId,
        String providerName,
        String modelCode,
        ModelCapabilityType capabilityType,
        long latencyMs,
        String message) {
}
