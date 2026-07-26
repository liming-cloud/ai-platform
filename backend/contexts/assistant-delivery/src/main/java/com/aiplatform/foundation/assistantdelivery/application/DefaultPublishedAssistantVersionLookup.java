package com.aiplatform.foundation.assistantdelivery.application;

import com.aiplatform.foundation.assistantdelivery.api.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.api.model.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionStatus;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 从助手仓储投影可信查询可用的已发布版本快照。
 */
public final class DefaultPublishedAssistantVersionLookup
        implements PublishedAssistantVersionLookup {

    private final AssistantRepository assistants;

    public DefaultPublishedAssistantVersionLookup(AssistantRepository assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手仓储不能为空");
    }

    @Override
    public Optional<PublishedAssistantVersionSnapshot> find(
            TenantId tenantId,
            UUID assistantVersionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        return assistants
                .findVersion(tenantId, new AssistantVersionId(assistantVersionId))
                .filter(version -> version.status() == AssistantVersionStatus.PUBLISHED)
                .map(version -> new PublishedAssistantVersionSnapshot(
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
