package org.fujay.ai.platform.model.interfaces.response;

import java.math.BigDecimal;
import java.util.List;

public record ModelProviderOptionsResponse(
        List<ProviderTypeOptionResponse> providerTypes, List<ModelOptionResponse> modelOptions) {

    public record ProviderTypeOptionResponse(
            String providerTypeId,
            String providerType,
            String displayName,
            String defaultBaseUrl,
            String status,
            Integer sortOrder) {
    }

    public record ModelOptionResponse(
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
}
