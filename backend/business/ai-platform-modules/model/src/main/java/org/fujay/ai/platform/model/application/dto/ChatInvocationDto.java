package org.fujay.ai.platform.model.application.dto;

import java.math.BigDecimal;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;

public record ChatInvocationDto(
        boolean success,
        Long providerId,
        Long modelId,
        String providerName,
        String modelCode,
        String displayName,
        ModelCapabilityType capabilityType,
        String traceId,
        long latencyMs,
        String output,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String usageMissingReason,
        BigDecimal costUsd,
        String costMissingReason) {
}
