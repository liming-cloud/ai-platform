package org.fujay.ai.platform.model.application.command;

import java.math.BigDecimal;
import java.util.List;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;

public record SaveModelProviderCommand(
        String providerName,
        ProviderType providerType,
        String baseUrl,
        String apiKey,
        BigDecimal budgetUsd,
        List<ModelCommand> models) {

    public record ModelCommand(
            String modelCode,
            String displayName,
            ModelCapabilityType capabilityType,
            ModelParameterDefaults defaults) {
    }
}
