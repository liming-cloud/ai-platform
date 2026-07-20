package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.AiApplicationCatalogResult;
import com.aiplatform.appstudio.application.result.AiApplicationDetailResult;
import java.time.Instant;
import java.util.List;

public record AiApplicationCatalogResponse(
        String applicationId,
        String applicationCode,
        String name,
        String description,
        String applicationType,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        String systemPrompt,
        String publicationStatus,
        Instant createdAt,
        Instant updatedAt,
        String activeReleaseId,
        Long activeReleaseVersion,
        String activeReleaseOperation,
        String sourceReleaseId,
        Long sourceReleaseVersion,
        String accessMode,
        ApplicationCapabilitiesResponse capabilities
) {
    public static AiApplicationCatalogResponse from(AiApplicationCatalogResult result) {
        AiApplicationDetailResult application = result.application();
        return new AiApplicationCatalogResponse(
                application.applicationId(),
                application.applicationCode(),
                application.name(),
                application.description(),
                application.applicationType(),
                application.modelConfigId(),
                application.knowledgeBaseIds(),
                application.systemPrompt(),
                application.publicationStatus(),
                application.createdAt(),
                application.updatedAt(),
                application.activeReleaseId(),
                application.activeReleaseVersion(),
                application.activeReleaseOperation(),
                application.sourceReleaseId(),
                application.sourceReleaseVersion(),
                result.accessMode().name(),
                ApplicationCapabilitiesResponse.from(result.capabilities())
        );
    }
}
