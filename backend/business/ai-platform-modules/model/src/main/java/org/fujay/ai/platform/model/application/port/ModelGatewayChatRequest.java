package org.fujay.ai.platform.model.application.port;

import java.math.BigDecimal;
import org.fujay.ai.platform.model.domain.value.ProviderType;

public record ModelGatewayChatRequest(
        ProviderType providerType,
        String baseUrl,
        String apiKeyCiphertext,
        String modelCode,
        String prompt,
        Integer maxOutputTokens,
        BigDecimal temperature,
        BigDecimal topP,
        BigDecimal frequencyPenalty,
        BigDecimal presencePenalty,
        String reasoningEffort) {
}
