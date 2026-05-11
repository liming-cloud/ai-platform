package org.fujay.ai.platform.model.interfaces.response;

import java.math.BigDecimal;

public record ModelResponse(
        String modelId,
        String providerId,
        String modelCode,
        String displayName,
        String capabilityType,
        String status,
        Integer contextWindow,
        Integer maxOutputTokens,
        Integer embeddingDimensions,
        BigDecimal temperature,
        BigDecimal topP,
        Integer topK,
        BigDecimal frequencyPenalty,
        BigDecimal presencePenalty,
        String reasoningEffort) {
}
