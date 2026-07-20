package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.CreateAiApplicationResult;
import java.time.Instant;
import java.util.List;

public record CreateAiApplicationResponse(
        String applicationId,
        String applicationCode,
        String name,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        String publicationStatus,
        Instant createdAt,
        String accessMode,
        ApplicationCapabilitiesResponse capabilities
) {
    public static CreateAiApplicationResponse from(CreateAiApplicationResult result) {
        return new CreateAiApplicationResponse(
                result.applicationId(),
                result.applicationCode(),
                result.name(),
                result.modelConfigId(),
                result.knowledgeBaseIds(),
                result.publicationStatus(),
                result.createdAt(),
                result.accessMode().name(),
                ApplicationCapabilitiesResponse.from(result.capabilities())
        );
    }
}
