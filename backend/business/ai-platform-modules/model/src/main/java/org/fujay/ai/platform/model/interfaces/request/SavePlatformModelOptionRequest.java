package org.fujay.ai.platform.model.interfaces.request;

import java.math.BigDecimal;

public record SavePlatformModelOptionRequest(
        String modelOptionId,
        String providerTypeId,
        String modelCode,
        String displayName,
        String capabilityType,
        Integer contextWindow,
        Integer maxOutputTokens,
        Integer embeddingDimensions,
        BigDecimal temperature,
        BigDecimal topP,
        Integer topK,
        BigDecimal frequencyPenalty,
        BigDecimal presencePenalty,
        String reasoningEffort,
        String status,
        Integer sortOrder) {
}
