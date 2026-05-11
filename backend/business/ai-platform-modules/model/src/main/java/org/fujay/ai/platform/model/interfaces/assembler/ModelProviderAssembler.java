package org.fujay.ai.platform.model.interfaces.assembler;

import java.util.List;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand.ModelCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformModelOptionCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformProviderTypeCommand;
import org.fujay.ai.platform.model.application.command.TestModelCommand;
import org.fujay.ai.platform.model.application.dto.ModelDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ModelOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ProviderTypeOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderDto;
import org.fujay.ai.platform.model.application.dto.ModelTestDto;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.model.interfaces.request.SaveModelProviderRequest;
import org.fujay.ai.platform.model.interfaces.request.SavePlatformModelOptionRequest;
import org.fujay.ai.platform.model.interfaces.request.SavePlatformProviderTypeRequest;
import org.fujay.ai.platform.model.interfaces.request.TestModelRequest;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse.ModelOptionResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelProviderOptionsResponse.ProviderTypeOptionResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelResponse;
import org.fujay.ai.platform.model.interfaces.response.ModelTestResponse;
import org.springframework.stereotype.Component;

@Component
public class ModelProviderAssembler {

    public SaveModelProviderCommand toCommand(SaveModelProviderRequest request) {
        return new SaveModelProviderCommand(
                request.providerName(),
                parseProviderType(request.providerType()),
                request.baseUrl(),
                request.apiKey(),
                request.budgetUsd(),
                (request.models() == null ? List.<SaveModelProviderRequest.ModelRequest>of() : request.models()).stream()
                        .map(this::toModelCommand)
                        .toList());
    }

    public TestModelCommand toCommand(TestModelRequest request) {
        return new TestModelCommand(parseId(request.modelId()), request.prompt());
    }

    public SavePlatformProviderTypeCommand toCommand(SavePlatformProviderTypeRequest request) {
        return new SavePlatformProviderTypeCommand(
                parseOptionalId(request.providerTypeId()),
                parseProviderType(request.providerType()),
                request.displayName(),
                request.defaultBaseUrl(),
                request.status(),
                request.sortOrder());
    }

    public SavePlatformModelOptionCommand toCommand(SavePlatformModelOptionRequest request) {
        return new SavePlatformModelOptionCommand(
                parseOptionalId(request.modelOptionId()),
                parseId(request.providerTypeId()),
                request.modelCode(),
                request.displayName(),
                parseCapabilityType(request.capabilityType()),
                new ModelParameterDefaults(
                        request.contextWindow(),
                        request.maxOutputTokens(),
                        request.embeddingDimensions(),
                        request.temperature(),
                        request.topP(),
                        request.topK(),
                        request.frequencyPenalty(),
                        request.presencePenalty(),
                        request.reasoningEffort()),
                request.status(),
                request.sortOrder());
    }

    public ModelProviderResponse toResponse(ModelProviderDto dto) {
        return new ModelProviderResponse(
                String.valueOf(dto.providerId()),
                dto.providerName(),
                dto.providerType().name(),
                dto.baseUrl(),
                dto.apiKeyLast4(),
                dto.status(),
                dto.budgetUsd(),
                dto.models().stream().map(this::toResponse).toList());
    }

    public ModelProviderOptionsResponse toResponse(ModelProviderOptionsDto dto) {
        return new ModelProviderOptionsResponse(
                dto.providerTypes().stream()
                        .map(this::toResponse)
                        .toList(),
                dto.modelOptions().stream()
                        .map(this::toResponse)
                        .toList());
    }

    public ProviderTypeOptionResponse toResponse(ProviderTypeOptionDto option) {
        return new ProviderTypeOptionResponse(
                String.valueOf(option.providerTypeId()),
                option.providerType().name(),
                option.displayName(),
                option.defaultBaseUrl(),
                option.status(),
                option.sortOrder());
    }

    public ModelOptionResponse toResponse(ModelOptionDto option) {
        ModelParameterDefaults defaults = option.defaults();
        return new ModelOptionResponse(
                String.valueOf(option.modelOptionId()),
                String.valueOf(option.providerTypeId()),
                option.modelCode(),
                option.displayName(),
                option.capabilityType().name(),
                defaults.contextWindow(),
                defaults.maxOutputTokens(),
                defaults.embeddingDimensions(),
                defaults.temperature(),
                defaults.topP(),
                defaults.topK(),
                defaults.frequencyPenalty(),
                defaults.presencePenalty(),
                defaults.reasoningEffort(),
                option.status(),
                option.sortOrder());
    }

    public ModelTestResponse toResponse(ModelTestDto dto) {
        return new ModelTestResponse(
                dto.success(),
                String.valueOf(dto.providerId()),
                String.valueOf(dto.modelId()),
                dto.providerName(),
                dto.modelCode(),
                dto.capabilityType().name(),
                dto.latencyMs(),
                dto.message());
    }

    private ModelCommand toModelCommand(SaveModelProviderRequest.ModelRequest request) {
        return new ModelCommand(
                request.modelCode(),
                request.displayName(),
                parseCapabilityType(request.capabilityType()),
                new ModelParameterDefaults(
                        request.contextWindow(),
                        request.maxOutputTokens(),
                        request.embeddingDimensions(),
                        request.temperature(),
                        request.topP(),
                        request.topK(),
                        request.frequencyPenalty(),
                        request.presencePenalty(),
                        request.reasoningEffort()));
    }

    private ModelResponse toResponse(ModelDto dto) {
        ModelParameterDefaults defaults = dto.defaults();
        return new ModelResponse(
                String.valueOf(dto.modelId()),
                String.valueOf(dto.providerId()),
                dto.modelCode(),
                dto.displayName(),
                dto.capabilityType().name(),
                dto.status(),
                defaults.contextWindow(),
                defaults.maxOutputTokens(),
                defaults.embeddingDimensions(),
                defaults.temperature(),
                defaults.topP(),
                defaults.topK(),
                defaults.frequencyPenalty(),
                defaults.presencePenalty(),
                defaults.reasoningEffort());
    }

    private ProviderType parseProviderType(String value) {
        if (value == null || value.isBlank()) {
            return ProviderType.OPENAI;
        }
        return ProviderType.valueOf(value.trim().toUpperCase());
    }

    private ModelCapabilityType parseCapabilityType(String value) {
        if (value == null || value.isBlank()) {
            return ModelCapabilityType.CHAT;
        }
        return ModelCapabilityType.valueOf(value.trim().toUpperCase());
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        return Long.parseLong(value);
    }

    private Long parseOptionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }
}
