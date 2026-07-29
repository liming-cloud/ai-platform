package com.aiplatform.foundation.assistantdelivery.domain.assistant.service;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CreatorId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * 维护助手版本号分配与版本快照的一致性。
 *
 * <p>助手和助手版本均为独立聚合根。本服务将连续版本号分配及其快照创建收敛为一个领域操作，
 * 防止应用层分别推进助手和构造版本事实。</p>
 */
public final class AssistantVersioning {

    /**
     * 为已加载的助手分配下一受控版本并冻结版本快照。
     *
     * @throws IllegalArgumentException 任一模型、索引或行为快照不完整时拒绝创建。
     */
    public AssistantVersionAllocation allocateGovernedDraft(
            Assistant assistant,
            AssistantVersionId versionId,
            GovernedChatModelBinding chat,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            GovernedEmbeddingModelBinding embedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy,
            CreatorId createdBy,
            Instant createdAt) {
        var expectedAssistant = Objects.requireNonNull(assistant, "助手不能为空");
        var advancedAssistant = expectedAssistant.advanceVersion();
        var version = AssistantVersion.governedDraft(
                expectedAssistant.tenantId(),
                Objects.requireNonNull(versionId, "助手版本标识不能为空"),
                expectedAssistant.id(),
                advancedAssistant.latestVersionNumber(),
                Objects.requireNonNull(chat, "对话模型绑定不能为空"),
                knowledgeSpaceId,
                indexVersionId,
                indexVersionRevision,
                Objects.requireNonNull(embedding, "嵌入模型绑定不能为空"),
                systemInstruction,
                maxOutputTokens,
                retrievalLimit,
                minimumScore,
                citationPolicy,
                refusalPolicy,
                createdBy,
                createdAt);
        return new AssistantVersionAllocation(expectedAssistant, advancedAssistant, version);
    }

    /**
     * 一次跨聚合版本分配的不可变结果。
     */
    public record AssistantVersionAllocation(
            Assistant expectedAssistant,
            Assistant advancedAssistant,
            AssistantVersion assistantVersion) {

        public AssistantVersionAllocation {
            Objects.requireNonNull(expectedAssistant, "原助手不能为空");
            Objects.requireNonNull(advancedAssistant, "新助手不能为空");
            Objects.requireNonNull(assistantVersion, "助手版本不能为空");
            if (!expectedAssistant.id().equals(advancedAssistant.id())
                    || !expectedAssistant.id().equals(assistantVersion.assistantId())
                    || advancedAssistant.version() != expectedAssistant.version() + 1
                    || advancedAssistant.latestVersionNumber()
                            != expectedAssistant.latestVersionNumber() + 1
                    || assistantVersion.number() != advancedAssistant.latestVersionNumber()) {
                throw new IllegalArgumentException("助手与新版本关系不正确");
            }
        }
    }
}
