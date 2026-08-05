package com.aiplatform.foundation.assistantdelivery.application.assistant.dto;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelRoute;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import java.util.Objects;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 助手负责人测试草稿时冻结使用的不可变版本事实。
 *
 * <p>该快照只经受限跨上下文门面提供，不能作为普通查询或渠道调用的助手版本。</p>
 */
public record DraftAssistantVersionSnapshot(
        long assistantVersionId,
        long assistantId,
        int number,
        long knowledgeSpaceId,
        long indexVersionId,
        long indexVersionRevision,
        GovernedChatModelBinding governedChat,
        GovernedChatModelRoute governedChatRoute,
        GovernedEmbeddingModelBinding governedEmbedding,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {

    public DraftAssistantVersionSnapshot(
            long assistantVersionId,
            long assistantId,
            int number,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            GovernedChatModelBinding governedChat,
            GovernedEmbeddingModelBinding governedEmbedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy) {
        this(
                assistantVersionId, assistantId, number, knowledgeSpaceId, indexVersionId,
                indexVersionRevision, governedChat, new GovernedChatModelRoute(List.of(governedChat)),
                governedEmbedding, systemInstruction, maxOutputTokens, retrievalLimit,
                minimumScore, citationPolicy, refusalPolicy);
    }

    /**
     * 创建冻结有序聊天候选的草稿助手快照。
     */
    public DraftAssistantVersionSnapshot(
            long assistantVersionId,
            long assistantId,
            int number,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            GovernedChatModelRoute governedChatRoute,
            GovernedEmbeddingModelBinding governedEmbedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy) {
        this(
                assistantVersionId, assistantId, number, knowledgeSpaceId, indexVersionId,
                indexVersionRevision, governedChatRoute.primary(), governedChatRoute,
                governedEmbedding, systemInstruction, maxOutputTokens, retrievalLimit,
                minimumScore, citationPolicy, refusalPolicy);
    }

    public DraftAssistantVersionSnapshot {
        if (assistantVersionId <= 0
                || assistantId <= 0
                || number < 1
                || knowledgeSpaceId <= 0
                || indexVersionId <= 0
                || indexVersionRevision < 1
                || maxOutputTokens < 1
                || retrievalLimit < 1
                || !Double.isFinite(minimumScore)
                || minimumScore < 0.0
                || minimumScore > 1.0) {
            throw new IllegalArgumentException("草稿助手版本快照不完整");
        }
        governedChat = Objects.requireNonNull(governedChat, "受控对话模型绑定不能为空");
        governedChatRoute = Objects.requireNonNull(governedChatRoute, "受控聊天路由不能为空");
        if (!governedChat.equals(governedChatRoute.primary())) {
            throw new IllegalArgumentException("草稿助手版本聊天路由主候选不一致");
        }
        governedEmbedding = Objects.requireNonNull(governedEmbedding, "受控嵌入模型绑定不能为空");
        systemInstruction = Objects.requireNonNull(systemInstruction, "系统指令不能为空");
        citationPolicy = Objects.requireNonNull(citationPolicy, "引用策略不能为空");
        refusalPolicy = Objects.requireNonNull(refusalPolicy, "拒答策略不能为空");
        if (citationPolicy != CitationPolicy.REQUIRED
                || refusalPolicy != RefusalPolicy.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("草稿助手版本必须执行可信回答策略");
        }
    }

    /**
     * 草稿测试只支持已冻结的受控模型配置。
     *
     * @return 始终为 {@code true}
     */
    public boolean usesGovernedModelConfigurations() {
        return true;
    }

    /**
     * 返回供跨上下文评测冻结的确定性路由摘要；不包含提示词、端点、凭据或正文。
     */
    public String modelRouteFingerprint() {
        var canonical = new StringBuilder();
        for (var candidate : governedChatRoute.candidates()) {
            canonical.append("chat.configurationId=").append(candidate.configurationId()).append('\n')
                    .append("chat.configurationRevision=").append(candidate.configurationRevision()).append('\n')
                    .append("chat.catalogEntryId=").append(candidate.catalogEntryId()).append('\n')
                    .append("chat.catalogEntryRevision=").append(candidate.catalogEntryRevision()).append('\n');
        }
        canonical.append("embedding.configurationId=").append(governedEmbedding.configurationId()).append('\n')
                .append("embedding.configurationRevision=").append(governedEmbedding.configurationRevision()).append('\n')
                .append("embedding.catalogEntryId=").append(governedEmbedding.catalogEntryId()).append('\n')
                .append("embedding.catalogEntryRevision=").append(governedEmbedding.catalogEntryRevision()).append('\n');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
