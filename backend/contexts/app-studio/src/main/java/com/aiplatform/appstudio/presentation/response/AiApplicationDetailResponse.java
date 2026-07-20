package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.AiApplicationDetailResult;
import java.time.Instant;
import java.util.List;

public record AiApplicationDetailResponse(
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
        Long sourceReleaseVersion
) {
    public static AiApplicationDetailResponse from(AiApplicationDetailResult result) {
        return new AiApplicationDetailResponse(
                result.applicationId(),
                result.applicationCode(),
                result.name(),
                result.description(),
                result.applicationType(),
                result.modelConfigId(),
                result.knowledgeBaseIds(),
                result.systemPrompt(),
                result.publicationStatus(),
                result.createdAt(),
                result.updatedAt(),
                result.activeReleaseId(),
                result.activeReleaseVersion(),
                result.activeReleaseOperation(),
                result.sourceReleaseId(),
                result.sourceReleaseVersion()
        );
    }
}
