package org.fujay.ai.platform.model.interfaces.response;

public record ModelTestResponse(
        boolean success,
        String providerId,
        String modelId,
        String providerName,
        String modelCode,
        String capabilityType,
        long latencyMs,
        String message) {
}
