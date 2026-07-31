package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.DraftAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import org.junit.jupiter.api.Test;

class AssistantTrustedAnswerTargetTest {

    @Test
    void normalizesPublishedAndDraftSnapshotsIntoTheSameGovernedTarget() {
        var published = PublishedAssistantVersionSnapshot.governed(
                11, 7, 3, 21, 31, 2,
                chatBinding(), embeddingBinding(), "仅根据证据回答", 256, 4, 0.2,
                CitationPolicy.REQUIRED, RefusalPolicy.INSUFFICIENT_EVIDENCE);
        var draft = new DraftAssistantVersionSnapshot(
                12, 7, 4, 21, 32, 3,
                chatBinding(), embeddingBinding(), "仅根据证据回答", 256, 4, 0.2,
                CitationPolicy.REQUIRED, RefusalPolicy.INSUFFICIENT_EVIDENCE);

        var publishedTarget = AssistantTrustedAnswerTarget.from(published);
        var draftTarget = AssistantTrustedAnswerTarget.from(draft);

        assertEquals(21, publishedTarget.knowledgeSpaceId());
        assertEquals(31, publishedTarget.indexVersionId());
        assertEquals(21, draftTarget.knowledgeSpaceId());
        assertEquals(32, draftTarget.indexVersionId());
        assertTrue(publishedTarget.usesGovernedModelConfigurations());
        assertTrue(draftTarget.usesGovernedModelConfigurations());
        assertEquals(publishedTarget.governedChat(), draftTarget.governedChat());
        assertEquals(publishedTarget.governedEmbedding(), draftTarget.governedEmbedding());
    }

    @Test
    void preservesPublishedLegacyDeploymentFactsForCompatibilityExecution() {
        var published = new PublishedAssistantVersionSnapshot(
                13, 7, 5, 61, 2, 21, 33, 4, 62, 3, 4,
                "仅根据证据回答", 256, 4, 0.2,
                CitationPolicy.REQUIRED, RefusalPolicy.INSUFFICIENT_EVIDENCE);

        var target = AssistantTrustedAnswerTarget.from(published);

        assertTrue(!target.usesGovernedModelConfigurations());
        assertEquals(61, target.chatDeploymentId());
        assertEquals(62, target.embeddingDeploymentId());
    }

    private static GovernedChatModelBinding chatBinding() {
        return new GovernedChatModelBinding(41, 5, 51, 6, 256);
    }

    private static GovernedEmbeddingModelBinding embeddingBinding() {
        return new GovernedEmbeddingModelBinding(42, 7, 52, 8, 4);
    }
}
