package com.aiplatform.foundation.assistantdelivery.application.answer.service;

import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerTarget;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedChatModelRoute;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.RefusalPolicy;
import java.util.Objects;

/** 在助手上下文内部把经授权的草稿快照映射为公开执行目标。 */
public final class AssistantTrustedAnswerTargets {
    private AssistantTrustedAnswerTargets() {
    }

    public static AssistantTrustedAnswerTarget fromDraft(DraftAssistantVersionSnapshot value) {
        Objects.requireNonNull(value, "草稿助手版本快照不能为空");
        return new AssistantTrustedAnswerTarget(
                value.assistantVersionId(), 0, 0,
                value.knowledgeSpaceId(), value.indexVersionId(), value.indexVersionRevision(),
                0, 0, value.governedEmbedding().dimension(),
                chat(value.governedChat()), route(value.governedChatRoute()),
                embedding(value.governedEmbedding()),
                value.systemInstruction(), value.maxOutputTokens(), value.retrievalLimit(),
                value.minimumScore(), CitationPolicy.valueOf(value.citationPolicy().name()),
                RefusalPolicy.valueOf(value.refusalPolicy().name()));
    }

    private static GovernedChatModelBinding chat(
            com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding value) {
        return new GovernedChatModelBinding(value.configurationId(), value.configurationRevision(),
                value.catalogEntryId(), value.catalogEntryRevision(), value.outputLimit());
    }

    private static GovernedChatModelRoute route(
            com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelRoute value) {
        return new GovernedChatModelRoute(value.candidates().stream()
                .map(AssistantTrustedAnswerTargets::chat).toList());
    }

    private static GovernedEmbeddingModelBinding embedding(
            com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding value) {
        return new GovernedEmbeddingModelBinding(value.configurationId(), value.configurationRevision(),
                value.catalogEntryId(), value.catalogEntryRevision(), value.dimension());
    }
}
