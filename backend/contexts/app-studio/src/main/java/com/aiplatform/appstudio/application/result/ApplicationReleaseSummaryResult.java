package com.aiplatform.appstudio.application.result;

import java.time.Instant;
import java.util.List;

public record ApplicationReleaseSummaryResult(
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
}
