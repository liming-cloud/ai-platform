package org.fujay.ai.platform.model.application.dto;

import java.math.BigDecimal;
import java.util.List;
import org.fujay.ai.platform.model.domain.value.ProviderType;

public record ModelProviderDto(
        Long providerId,
        String providerName,
        ProviderType providerType,
        String baseUrl,
        String apiKeyLast4,
        String status,
        BigDecimal budgetUsd,
        List<ModelDto> models) {
}
