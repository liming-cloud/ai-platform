package org.fujay.ai.platform.ai;

import java.math.BigDecimal;

public record ChatInvocationCommand(
        AiProviderType providerType,
        String baseUrl,
        String apiKey,
        String modelCode,
        String prompt,
        Integer maxOutputTokens,
        BigDecimal temperature,
        BigDecimal topP,
        BigDecimal frequencyPenalty,
        BigDecimal presencePenalty,
        String reasoningEffort) {
}
