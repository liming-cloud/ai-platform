package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ApplicationReleaseDetailResult;
import java.time.Instant;
import java.util.List;

public record ApplicationReleaseDetailResponse(
        String releaseId,
        long version,
        boolean active,
        String operation,
        String sourceReleaseId,
        Long sourceReleaseVersion,
        String applicationId,
        String name,
        String description,
        String applicationType,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        List<String> toolIds,
        String systemPrompt,
        String systemPromptSha256,
        int ownerUserCount,
        int allowedUserCount,
        String publishedBy,
        String reason,
        String traceId,
        Instant applicationCreatedAt,
        Instant publishedAt
) {
    public static ApplicationReleaseDetailResponse from(ApplicationReleaseDetailResult result) {
        return new ApplicationReleaseDetailResponse(
                result.releaseId(),
                result.version(),
                result.active(),
                result.operation(),
                result.sourceReleaseId(),
                result.sourceReleaseVersion(),
                result.applicationId(),
                result.name(),
                result.description(),
                result.applicationType(),
                result.modelConfigId(),
                result.knowledgeBaseIds(),
                result.toolIds(),
                result.systemPrompt(),
                result.systemPromptSha256(),
                result.ownerUserCount(),
                result.allowedUserCount(),
                result.publishedBy(),
                result.reason(),
                result.traceId(),
                result.applicationCreatedAt(),
                result.publishedAt()
        );
    }
}
