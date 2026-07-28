package com.aiplatform.foundation.assistantdelivery.application.assistant.service;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * 从助手仓储投影可信查询可用的已发布版本快照。
 */
public final class PublishedAssistantVersionLookupService
        implements PublishedAssistantVersionLookup {

    private final AssistantRepository assistants;

    public PublishedAssistantVersionLookupService(AssistantRepository assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手仓储不能为空");
    }

    @Override
    public Optional<PublishedAssistantVersionSnapshot> find(
            TenantId tenantId,
            long assistantVersionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (assistantVersionId <= 0) {
            throw new IllegalArgumentException("助手版本标识必须是正整数");
        }
        return assistants
                .findVersion(tenantId, new AssistantVersionId(assistantVersionId))
                .filter(version -> version.status() == AssistantVersionStatus.PUBLISHED)
                .map(version -> version.usesGovernedModelConfigurations()
                        ? PublishedAssistantVersionSnapshot.governed(
                                version.id().value(),
                                version.assistantId().value(),
                                version.number(),
                                version.knowledgeSpaceId(),
                                version.indexVersionId(),
                                version.indexVersionRevision(),
                                version.governedChat(),
                                version.governedEmbedding(),
                                version.systemInstruction(),
                                version.maxOutputTokens(),
                                version.retrievalLimit(),
                                version.minimumScore(),
                                version.citationPolicy(),
                                version.refusalPolicy())
                        : new PublishedAssistantVersionSnapshot(
                                version.id().value(),
                                version.assistantId().value(),
                                version.number(),
                                version.chatDeploymentId(),
                                version.chatDeploymentRevision(),
                                version.knowledgeSpaceId(),
                                version.indexVersionId(),
                                version.indexVersionRevision(),
                                version.embeddingDeploymentId(),
                                version.embeddingDeploymentRevision(),
                                version.embeddingDimension(),
                                version.systemInstruction(),
                                version.maxOutputTokens(),
                                version.retrievalLimit(),
                                version.minimumScore(),
                                version.citationPolicy(),
                                version.refusalPolicy()));
    }
}
