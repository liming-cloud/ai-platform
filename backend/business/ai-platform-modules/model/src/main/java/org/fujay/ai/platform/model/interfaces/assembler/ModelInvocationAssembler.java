package org.fujay.ai.platform.model.interfaces.assembler;

import org.fujay.ai.platform.model.application.command.ChatInvocationCommand;
import org.fujay.ai.platform.model.application.dto.ChatInvocationDto;
import org.fujay.ai.platform.model.interfaces.request.ChatInvocationRequest;
import org.fujay.ai.platform.model.interfaces.response.ChatInvocationResponse;
import org.springframework.stereotype.Component;

@Component
public class ModelInvocationAssembler {

    public ChatInvocationCommand toCommand(ChatInvocationRequest request) {
        return new ChatInvocationCommand(request.modelId(), request.prompt());
    }

    public ChatInvocationResponse toResponse(ChatInvocationDto dto) {
        return new ChatInvocationResponse(
                dto.success(),
                String.valueOf(dto.providerId()),
                String.valueOf(dto.modelId()),
                dto.providerName(),
                dto.modelCode(),
                dto.displayName(),
                dto.capabilityType().name(),
                dto.traceId(),
                dto.latencyMs(),
                dto.output(),
                dto.promptTokens(),
                dto.completionTokens(),
                dto.totalTokens(),
                dto.usageMissingReason(),
                dto.costUsd(),
                dto.costMissingReason());
    }
}
