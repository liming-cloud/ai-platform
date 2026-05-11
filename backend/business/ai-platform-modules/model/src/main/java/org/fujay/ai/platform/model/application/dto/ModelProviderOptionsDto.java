package org.fujay.ai.platform.model.application.dto;

import java.util.List;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;

public record ModelProviderOptionsDto(List<ProviderTypeOptionDto> providerTypes, List<ModelOptionDto> modelOptions) {

    public record ProviderTypeOptionDto(
            Long providerTypeId,
            ProviderType providerType,
            String displayName,
            String defaultBaseUrl,
            String status,
            Integer sortOrder) {
    }

    public record ModelOptionDto(
            Long modelOptionId,
            Long providerTypeId,
            String modelCode,
            String displayName,
            ModelCapabilityType capabilityType,
            ModelParameterDefaults defaults,
            String status,
            Integer sortOrder) {
    }
}
