package com.aiplatform.foundation.assistantdelivery.api.model;

import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import java.time.Instant;
import java.util.UUID;

/**
 * 助手版本的公共只读视图。
 */
public record AssistantVersionView(
        UUID id,
        UUID assistantId,
        int number,
        UUID chatDeploymentId,
        long chatDeploymentRevision,
        String knowledgeSpaceId,
        String indexVersionId,
        long indexVersionRevision,
        UUID embeddingDeploymentId,
        long embeddingDeploymentRevision,
        int embeddingDimension,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy,
        String status,
        long version,
        Instant createdAt,
        Instant publishedAt) {
}
