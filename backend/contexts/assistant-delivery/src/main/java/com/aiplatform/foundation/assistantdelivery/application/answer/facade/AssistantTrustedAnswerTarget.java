package com.aiplatform.foundation.assistantdelivery.application.answer.facade;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedChatModelRoute;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.RefusalPolicy;
import java.util.Objects;

/**
 * 已冻结助手快照归一后的可信回答目标。
 *
 * <p>该值对象只承载本次执行需要的版本事实，不携带运行根、配额、事件或持久化正文。</p>
 */
public record AssistantTrustedAnswerTarget(
        long assistantVersionId,
        long chatDeploymentId,
        long chatDeploymentRevision,
        long knowledgeSpaceId,
        long indexVersionId,
        long indexVersionRevision,
        long embeddingDeploymentId,
        long embeddingDeploymentRevision,
        int embeddingDimension,
        GovernedChatModelBinding governedChat,
        GovernedChatModelRoute governedChatRoute,
        GovernedEmbeddingModelBinding governedEmbedding,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {

    public AssistantTrustedAnswerTarget {
        if (assistantVersionId <= 0
                || knowledgeSpaceId <= 0
                || indexVersionId <= 0
                || indexVersionRevision < 1
                || embeddingDimension < 1
                || maxOutputTokens < 1
                || retrievalLimit < 1
                || !Double.isFinite(minimumScore)
                || minimumScore < 0.0
                || minimumScore > 1.0) {
            throw new IllegalArgumentException("可信回答目标不完整");
        }
        systemInstruction = Objects.requireNonNull(systemInstruction, "系统指令不能为空");
        citationPolicy = Objects.requireNonNull(citationPolicy, "引用策略不能为空");
        refusalPolicy = Objects.requireNonNull(refusalPolicy, "拒答策略不能为空");
        var legacy = governedChat == null && governedChatRoute == null && governedEmbedding == null;
        if ((legacy && (chatDeploymentId <= 0
                || chatDeploymentRevision < 1
                || embeddingDeploymentId <= 0
                || embeddingDeploymentRevision < 1))
                || (!legacy && (governedChat == null
                || governedChatRoute == null
                || !governedChat.equals(governedChatRoute.primary())
                || governedEmbedding == null
                || chatDeploymentId != 0
                || chatDeploymentRevision != 0
                || embeddingDeploymentId != 0
                || embeddingDeploymentRevision != 0))
                || citationPolicy != CitationPolicy.REQUIRED
                || refusalPolicy != RefusalPolicy.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("可信回答目标的模型绑定或可信策略不正确");
        }
    }

    /**
     * 将已发布且采用受控模型配置的助手快照转换为执行目标。
     *
     */
    public static AssistantTrustedAnswerTarget from(PublishedAssistantVersionSnapshot value) {
        Objects.requireNonNull(value, "已发布助手版本快照不能为空");
        return new AssistantTrustedAnswerTarget(
                value.assistantVersionId(), value.chatDeploymentId(), value.chatDeploymentRevision(),
                value.knowledgeSpaceId(), value.indexVersionId(), value.indexVersionRevision(),
                value.embeddingDeploymentId(), value.embeddingDeploymentRevision(), value.embeddingDimension(),
                value.governedChat(), value.governedChatRoute(), value.governedEmbedding(),
                value.systemInstruction(), value.maxOutputTokens(), value.retrievalLimit(),
                value.minimumScore(), value.citationPolicy(), value.refusalPolicy());
    }

    /**
     * 是否使用 M2 受控模型配置；否则使用已发布版本冻结的兼容部署事实。
     */
    public boolean usesGovernedModelConfigurations() {
        return governedChat != null;
    }

}
