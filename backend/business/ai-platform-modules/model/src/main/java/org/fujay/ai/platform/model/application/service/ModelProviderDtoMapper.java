package org.fujay.ai.platform.model.application.service;

import java.util.List;
import org.fujay.ai.platform.model.application.dto.ModelDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ModelOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ProviderTypeOptionDto;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.aggregate.PlatformModelOption;
import org.fujay.ai.platform.model.domain.aggregate.PlatformProviderTypeOption;

final class ModelProviderDtoMapper {

    private ModelProviderDtoMapper() {
    }

    static ModelProviderDto toProviderDto(ModelProvider provider) {
        return new ModelProviderDto(
                provider.id(),
                provider.providerName(),
                provider.providerType(),
                provider.baseUrl(),
                provider.apiKeyLast4(),
                provider.status(),
                provider.budgetUsd(),
                provider.models().stream().map(ModelProviderDtoMapper::toModelDto).toList());
    }

    static ModelProviderOptionsDto toOptionsDto(
            List<PlatformProviderTypeOption> providerTypes, List<PlatformModelOption> modelOptions) {
        return new ModelProviderOptionsDto(
                providerTypes.stream().map(ModelProviderDtoMapper::toProviderTypeOptionDto).toList(),
                modelOptions.stream().map(ModelProviderDtoMapper::toModelOptionDto).toList());
    }

    static ProviderTypeOptionDto toProviderTypeOptionDto(PlatformProviderTypeOption option) {
        return new ProviderTypeOptionDto(
                option.id(),
                option.providerType(),
                option.displayName(),
                option.defaultBaseUrl(),
                option.status(),
                option.sortOrder());
    }

    static ModelOptionDto toModelOptionDto(PlatformModelOption option) {
        return new ModelOptionDto(
                option.id(),
                option.providerTypeId(),
                option.modelCode(),
                option.displayName(),
                option.capabilityType(),
                option.defaults(),
                option.status(),
                option.sortOrder());
    }

    static ModelDto toModelDto(ConfiguredModel model) {
        return new ModelDto(
                model.id(),
                model.providerId(),
                model.modelCode(),
                model.displayName(),
                model.capabilityType(),
                model.status(),
                model.defaults());
    }
}
