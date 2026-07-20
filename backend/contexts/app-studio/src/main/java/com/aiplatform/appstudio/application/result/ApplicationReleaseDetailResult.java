package com.aiplatform.appstudio.application.result;

import java.time.Instant;
import java.util.List;

public record ApplicationReleaseDetailResult(
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
}
