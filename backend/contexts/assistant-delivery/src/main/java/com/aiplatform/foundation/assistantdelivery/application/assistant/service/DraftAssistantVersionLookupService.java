package com.aiplatform.foundation.assistantdelivery.application.assistant.service;

import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.DraftAssistantVersionLookup;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import java.util.Optional;

/**
 * 从助手仓储投影当前主体可测试的草稿版本快照。
 */
public final class DraftAssistantVersionLookupService
        implements DraftAssistantVersionLookup {

    private static final PermissionCode WRITE = new PermissionCode("assistant:write");

    private final AssistantRepository assistants;

    public DraftAssistantVersionLookupService(AssistantRepository assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手仓储不能为空");
    }

    @Override
    public Optional<DraftAssistantVersionSnapshot> find(
            TrustedRequestContext context,
            long assistantVersionId) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(WRITE)) {
            throw new AssistantPermissionDeniedException();
        }
        if (assistantVersionId <= 0) {
            throw new IllegalArgumentException("助手版本标识必须是正整数");
        }
        return assistants
                .findVersion(context.tenantId(), new AssistantVersionId(assistantVersionId))
                .filter(version -> version.status() == AssistantVersionStatus.DRAFT)
                .filter(version -> version.usesGovernedModelConfigurations())
                .map(version -> new DraftAssistantVersionSnapshot(
                        version.id().value(),
                        version.assistantId().value(),
                        version.number(),
                        version.knowledgeSpaceId(),
                        version.indexVersionId(),
                        version.indexVersionRevision(),
                        version.governedChat(),
                        version.governedChatRoute(),
                        version.governedEmbedding(),
                        version.systemInstruction(),
                        version.maxOutputTokens(),
                        version.retrievalLimit(),
                        version.minimumScore(),
                        version.citationPolicy(),
                        version.refusalPolicy()));
    }
}
