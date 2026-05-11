package org.fujay.ai.platform.model.infrastructure.bootstrap;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.platform.model.bootstrap.openai")
public record OpenAiProviderBootstrapProperties(
        boolean enabled,
        String tenantCode,
        String providerName,
        String apiKey,
        String chatModel,
        BigDecimal budgetUsd,
        Integer contextWindow,
        Integer maxOutputTokens,
        String reasoningEffort) {
}
