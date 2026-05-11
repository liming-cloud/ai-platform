package org.fujay.ai.platform.model.interfaces.response;

import java.math.BigDecimal;
import java.util.List;

public record ModelProviderResponse(
        String providerId,
        String providerName,
        String providerType,
        String baseUrl,
        String apiKeyLast4,
        String status,
        BigDecimal budgetUsd,
        List<ModelResponse> models) {
}
