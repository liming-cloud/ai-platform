package org.fujay.ai.platform.model.interfaces.response;

import java.math.BigDecimal;

public record ChatInvocationResponse(
        boolean success,
        String providerId,
        String modelId,
        String providerName,
        String modelCode,
        String displayName,
        String capabilityType,
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
