package com.aiplatform.foundation.assistantdelivery.application.assistant.dto;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import java.time.Instant;

/**
 * 助手版本的公共只读视图。
 */
public record AssistantVersionView(
        String id,
        String assistantId,
        int number,
        String chatDeploymentId,
        long chatDeploymentRevision,
        String chatModelConfigurationId,
        long chatModelConfigurationRevision,
        String chatCatalogEntryId,
        long chatCatalogEntryRevision,
        String knowledgeSpaceId,
        String indexVersionId,
        long indexVersionRevision,
        String embeddingDeploymentId,
        long embeddingDeploymentRevision,
        String embeddingModelConfigurationId,
        long embeddingModelConfigurationRevision,
        String embeddingCatalogEntryId,
        long embeddingCatalogEntryRevision,
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
