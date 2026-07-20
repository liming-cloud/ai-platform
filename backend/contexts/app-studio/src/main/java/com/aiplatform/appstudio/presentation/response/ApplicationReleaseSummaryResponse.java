package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ApplicationReleaseSummaryResult;
import java.time.Instant;
import java.util.List;

public record ApplicationReleaseSummaryResponse(
        String releaseId,
        long version,
        boolean active,
        String operation,
        String sourceReleaseId,
        Long sourceReleaseVersion,
        String name,
        String applicationType,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        String systemPromptSummary,
        String systemPromptSha256,
        int ownerUserCount,
        int allowedUserCount,
        String publishedBy,
        String reason,
        String traceId,
        Instant createdAt,
        Instant publishedAt
) {
    public static ApplicationReleaseSummaryResponse from(ApplicationReleaseSummaryResult result) {
        return new ApplicationReleaseSummaryResponse(
                result.releaseId(),
                result.version(),
                result.active(),
                result.operation(),
                result.sourceReleaseId(),
                result.sourceReleaseVersion(),
                result.name(),
                result.applicationType(),
                result.modelConfigId(),
                result.knowledgeBaseIds(),
                result.systemPromptSummary(),
                result.systemPromptSha256(),
                result.ownerUserCount(),
                result.allowedUserCount(),
                result.publishedBy(),
                result.reason(),
                result.traceId(),
                result.createdAt(),
                result.publishedAt()
        );
    }
}
