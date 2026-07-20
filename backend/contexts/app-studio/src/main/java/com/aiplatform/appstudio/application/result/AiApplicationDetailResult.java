package com.aiplatform.appstudio.application.result;

import java.time.Instant;
import java.util.List;

/**
 * AI 应用详情结果，供更新、发布与后续列表/详情接口复用。
 */
public record AiApplicationDetailResult(
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
}
