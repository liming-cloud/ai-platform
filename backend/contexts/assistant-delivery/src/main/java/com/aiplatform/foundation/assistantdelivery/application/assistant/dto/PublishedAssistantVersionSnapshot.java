package com.aiplatform.foundation.assistantdelivery.application.assistant.dto;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelRoute;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import java.util.Objects;
import java.util.List;

/**
 * 可信查询执行时冻结使用的已发布助手版本事实。
 */
public record PublishedAssistantVersionSnapshot(
        long assistantVersionId,
        long assistantId,
        int number,
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

    public PublishedAssistantVersionSnapshot(
            long assistantVersionId,
            long assistantId,
            int number,
            long chatDeploymentId,
            long chatDeploymentRevision,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            long embeddingDeploymentId,
            long embeddingDeploymentRevision,
            int embeddingDimension,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy) {
        this(
                assistantVersionId, assistantId, number,
                chatDeploymentId, chatDeploymentRevision,
                knowledgeSpaceId, indexVersionId, indexVersionRevision,
                embeddingDeploymentId, embeddingDeploymentRevision, embeddingDimension,
                null, null, null,
                systemInstruction, maxOutputTokens, retrievalLimit, minimumScore,
                citationPolicy, refusalPolicy);
    }

    /**
     * 创建冻结受控 Chat 与 Embedding 配置的已发布助手快照。
     */
    public static PublishedAssistantVersionSnapshot governed(
            long assistantVersionId,
            long assistantId,
            int number,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            GovernedChatModelBinding chat,
            GovernedEmbeddingModelBinding embedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy) {
        return governed(
                assistantVersionId, assistantId, number, knowledgeSpaceId, indexVersionId,
                indexVersionRevision, new GovernedChatModelRoute(List.of(chat)), embedding,
                systemInstruction, maxOutputTokens, retrievalLimit, minimumScore,
                citationPolicy, refusalPolicy);
    }

    /**
     * 创建冻结有序 Chat 路由与 Embedding 配置的已发布助手快照。
     */
    public static PublishedAssistantVersionSnapshot governed(
            long assistantVersionId,
            long assistantId,
            int number,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            GovernedChatModelRoute chatRoute,
            GovernedEmbeddingModelBinding embedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy) {
        return new PublishedAssistantVersionSnapshot(
                assistantVersionId, assistantId, number,
                0, 0,
                knowledgeSpaceId, indexVersionId, indexVersionRevision,
                0, 0, embedding.dimension(),
                chatRoute.primary(), chatRoute, embedding,
                systemInstruction, maxOutputTokens, retrievalLimit, minimumScore,
                citationPolicy, refusalPolicy);
    }

    public PublishedAssistantVersionSnapshot {
        if (assistantVersionId <= 0
                || assistantId <= 0
                || knowledgeSpaceId <= 0
                || indexVersionId <= 0) {
            throw new IllegalArgumentException("助手、知识空间和索引版本标识必须是正整数");
        }
        Objects.requireNonNull(systemInstruction, "系统指令不能为空");
        Objects.requireNonNull(citationPolicy, "引用策略不能为空");
        Objects.requireNonNull(refusalPolicy, "拒答策略不能为空");
        var legacy = governedChat == null && governedChatRoute == null && governedEmbedding == null;
        if ((legacy && (chatDeploymentId <= 0
                || embeddingDeploymentId <= 0
                || chatDeploymentRevision < 1
                || embeddingDeploymentRevision < 1))
                || (!legacy && (governedChat == null
                || governedChatRoute == null
                || !governedChat.equals(governedChatRoute.primary())
                || governedEmbedding == null
                || chatDeploymentId != 0
                || chatDeploymentRevision != 0
                || embeddingDeploymentId != 0
                || embeddingDeploymentRevision != 0))
                || number < 1
                || indexVersionRevision < 1
                || embeddingDimension < 1
                || maxOutputTokens < 1
                || retrievalLimit < 1
                || !Double.isFinite(minimumScore)
                || minimumScore < 0.0
                || minimumScore > 1.0) {
            throw new IllegalArgumentException("已发布助手版本快照不完整");
        }
        if (citationPolicy != CitationPolicy.REQUIRED
                || refusalPolicy != RefusalPolicy.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("已发布助手版本必须执行可信回答策略");
        }
    }

    public boolean usesGovernedModelConfigurations() {
        return governedChat != null;
    }
}
