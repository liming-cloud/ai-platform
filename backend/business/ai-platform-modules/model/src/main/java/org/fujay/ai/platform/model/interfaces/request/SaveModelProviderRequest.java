package org.fujay.ai.platform.model.interfaces.request;

import java.math.BigDecimal;
import java.util.List;

public record SaveModelProviderRequest(
        String providerName,
        String providerType,
        String baseUrl,
        String apiKey,
        BigDecimal budgetUsd,
        List<ModelRequest> models) {

    public record ModelRequest(
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
            String reasoningEffort) {
    }
}
