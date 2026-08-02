package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 冻结模型、索引、提示和可信回答策略的助手版本。
 */
public final class AssistantVersion implements AggregateRoot<AssistantVersionId> {

    private static final int MAX_INSTRUCTION_CODE_POINTS = 8000;
    private static final int MAX_RETRIEVAL_LIMIT = 20;

    private final TenantId tenantId;
    private final AssistantVersionId id;
    private final AssistantId assistantId;
    private final int number;
    private final long chatDeploymentId;
    private final long chatDeploymentRevision;
    private final int chatDeploymentOutputLimit;
    private final long knowledgeSpaceId;
    private final long indexVersionId;
    private final long indexVersionRevision;
    private final long embeddingDeploymentId;
    private final long embeddingDeploymentRevision;
    private final int embeddingDimension;
    private final GovernedChatModelBinding governedChat;
    private final GovernedChatModelRoute governedChatRoute;
    private final GovernedEmbeddingModelBinding governedEmbedding;
    private final String systemInstruction;
    private final int maxOutputTokens;
    private final int retrievalLimit;
    private final double minimumScore;
    private final CitationPolicy citationPolicy;
    private final RefusalPolicy refusalPolicy;
    private final AssistantVersionStatus status;
    private final long version;
    private final CreatorId createdBy;
    private final Instant createdAt;
    private final Instant publishedAt;

    private AssistantVersion(
            TenantId tenantId,
            AssistantVersionId id,
            AssistantId assistantId,
            int number,
            long chatDeploymentId,
            long chatDeploymentRevision,
            int chatDeploymentOutputLimit,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            long embeddingDeploymentId,
            long embeddingDeploymentRevision,
            int embeddingDimension,
            GovernedChatModelRoute governedChatRoute,
            GovernedEmbeddingModelBinding governedEmbedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy,
            AssistantVersionStatus status,
            long version,
            CreatorId createdBy,
            Instant createdAt,
            Instant publishedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "助手版本标识不能为空");
        this.assistantId = Objects.requireNonNull(assistantId, "助手标识不能为空");
        if (number < 1) {
            throw new IllegalArgumentException("助手版本号必须大于零");
        }
        this.number = number;
        if (knowledgeSpaceId <= 0 || indexVersionId <= 0) {
            throw new IllegalArgumentException("知识空间标识和索引版本标识必须是正整数");
        }
        this.knowledgeSpaceId = knowledgeSpaceId;
        this.indexVersionId = indexVersionId;
        this.indexVersionRevision = indexVersionRevision;
        if (indexVersionRevision < 1 || embeddingDimension < 1) {
            throw new IllegalArgumentException("索引修订和嵌入维度必须大于零");
        }
        var governedChat = governedChatRoute == null ? null : governedChatRoute.primary();
        var legacy = governedChatRoute == null && governedEmbedding == null;
        if (legacy) {
            if (chatDeploymentId <= 0
                    || chatDeploymentRevision < 1
                    || chatDeploymentOutputLimit < 1
                    || embeddingDeploymentId <= 0
                    || embeddingDeploymentRevision < 1) {
                throw new IllegalArgumentException("历史模型部署快照不完整");
            }
        } else if (governedChatRoute == null || governedEmbedding == null
                || chatDeploymentId != 0
                || chatDeploymentRevision != 0
                || embeddingDeploymentId != 0
                || embeddingDeploymentRevision != 0) {
            throw new IllegalArgumentException("助手版本必须完整且唯一地使用历史部署或受控模型配置");
        }
        this.chatDeploymentId = chatDeploymentId;
        this.chatDeploymentRevision = chatDeploymentRevision;
        this.chatDeploymentOutputLimit = legacy
                ? chatDeploymentOutputLimit
                : governedChat.outputLimit();
        this.embeddingDeploymentId = embeddingDeploymentId;
        this.embeddingDeploymentRevision = embeddingDeploymentRevision;
        this.embeddingDimension = embeddingDimension;
        this.governedChat = governedChat;
        this.governedChatRoute = governedChatRoute;
        this.governedEmbedding = governedEmbedding;
        this.systemInstruction = requireInstruction(systemInstruction);
        if (maxOutputTokens < 1 || maxOutputTokens > this.chatDeploymentOutputLimit) {
            throw new IllegalArgumentException("回答上限必须在模型允许范围内");
        }
        if (retrievalLimit < 1 || retrievalLimit > MAX_RETRIEVAL_LIMIT) {
            throw new IllegalArgumentException("检索条数必须在 1 到 20 之间");
        }
        if (!Double.isFinite(minimumScore)
                || minimumScore < 0.0
                || minimumScore > 1.0) {
            throw new IllegalArgumentException("最低相似度必须在 0 到 1 之间");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.retrievalLimit = retrievalLimit;
        this.minimumScore = minimumScore;
        this.citationPolicy = Objects.requireNonNull(citationPolicy, "引用策略不能为空");
        this.refusalPolicy = Objects.requireNonNull(refusalPolicy, "拒答策略不能为空");
        if (citationPolicy != CitationPolicy.REQUIRED
                || refusalPolicy != RefusalPolicy.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("当前版本必须要求引用并在证据不足时拒答");
        }
        this.status = Objects.requireNonNull(status, "助手版本状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("助手版本修订不能为负数");
        }
        this.version = version;
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.publishedAt = publishedAt;
        if ((status == AssistantVersionStatus.PUBLISHED) != (publishedAt != null)) {
            throw new IllegalArgumentException("助手版本状态与发布时间不一致");
        }
    }

    public static AssistantVersion draft(
            TenantId tenantId,
            AssistantVersionId id,
            AssistantId assistantId,
            int number,
            long chatDeploymentId,
            long chatDeploymentRevision,
            int chatDeploymentOutputLimit,
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
            RefusalPolicy refusalPolicy,
            CreatorId createdBy,
            Instant createdAt) {
        return new AssistantVersion(
                tenantId,
                id,
                assistantId,
                number,
                chatDeploymentId,
                chatDeploymentRevision,
                chatDeploymentOutputLimit,
                knowledgeSpaceId,
                indexVersionId,
                indexVersionRevision,
                embeddingDeploymentId,
                embeddingDeploymentRevision,
                embeddingDimension,
                null,
                null,
                systemInstruction,
                maxOutputTokens,
                retrievalLimit,
                minimumScore,
                citationPolicy,
                refusalPolicy,
                AssistantVersionStatus.DRAFT,
                0,
                createdBy,
                createdAt,
                null);
    }

    /**
     * 创建仅可由受控模型配置驱动的助手草稿；嵌入配置必须已与活动索引同源。
     */
    public static AssistantVersion governedDraft(
            TenantId tenantId,
            AssistantVersionId id,
            AssistantId assistantId,
            int number,
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
        return new AssistantVersion(
                tenantId, id, assistantId, number,
                0, 0, 0,
                knowledgeSpaceId, indexVersionId, indexVersionRevision,
                0, 0, embedding.dimension(),
                new GovernedChatModelRoute(List.of(chat)), embedding,
                systemInstruction, maxOutputTokens, retrievalLimit, minimumScore,
                citationPolicy, refusalPolicy, AssistantVersionStatus.DRAFT, 0,
                createdBy, createdAt, null);
    }

    /**
     * 创建冻结有序聊天候选的受控助手草稿；嵌入配置仍保持与活动索引同源的单一绑定。
     */
    public static AssistantVersion governedDraft(
            TenantId tenantId,
            AssistantVersionId id,
            AssistantId assistantId,
            int number,
            GovernedChatModelRoute chatRoute,
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
        return new AssistantVersion(
                tenantId, id, assistantId, number,
                0, 0, 0,
                knowledgeSpaceId, indexVersionId, indexVersionRevision,
                0, 0, embedding.dimension(),
                chatRoute, embedding,
                systemInstruction, maxOutputTokens, retrievalLimit, minimumScore,
                citationPolicy, refusalPolicy, AssistantVersionStatus.DRAFT, 0,
                createdBy, createdAt, null);
    }

    public static AssistantVersion reconstitute(
            TenantId tenantId,
            AssistantVersionId id,
            AssistantId assistantId,
            int number,
            long chatDeploymentId,
            long chatDeploymentRevision,
            int chatDeploymentOutputLimit,
            long knowledgeSpaceId,
            long indexVersionId,
            long indexVersionRevision,
            long embeddingDeploymentId,
            long embeddingDeploymentRevision,
            int embeddingDimension,
            GovernedChatModelBinding governedChat,
            GovernedEmbeddingModelBinding governedEmbedding,
            String systemInstruction,
            int maxOutputTokens,
            int retrievalLimit,
            double minimumScore,
            CitationPolicy citationPolicy,
            RefusalPolicy refusalPolicy,
            AssistantVersionStatus status,
            long version,
            CreatorId createdBy,
            Instant createdAt,
            Instant publishedAt) {
        return new AssistantVersion(
                tenantId,
                id,
                assistantId,
                number,
                chatDeploymentId,
                chatDeploymentRevision,
                chatDeploymentOutputLimit,
                knowledgeSpaceId,
                indexVersionId,
                indexVersionRevision,
                embeddingDeploymentId,
                embeddingDeploymentRevision,
                embeddingDimension,
                governedChat == null ? null : new GovernedChatModelRoute(List.of(governedChat)),
                governedEmbedding,
                systemInstruction,
                maxOutputTokens,
                retrievalLimit,
                minimumScore,
                citationPolicy,
                refusalPolicy,
                status,
                version,
                createdBy,
                createdAt,
                publishedAt);
    }

    public AssistantVersion publish(Instant time) {
        if (status != AssistantVersionStatus.DRAFT) {
            throw new AssistantVersionStateException("只有草稿助手版本可以发布");
        }
        return new AssistantVersion(
                tenantId,
                id,
                assistantId,
                number,
                chatDeploymentId,
                chatDeploymentRevision,
                chatDeploymentOutputLimit,
                knowledgeSpaceId,
                indexVersionId,
                indexVersionRevision,
                embeddingDeploymentId,
                embeddingDeploymentRevision,
                embeddingDimension,
                governedChatRoute,
                governedEmbedding,
                systemInstruction,
                maxOutputTokens,
                retrievalLimit,
                minimumScore,
                citationPolicy,
                refusalPolicy,
                AssistantVersionStatus.PUBLISHED,
                version + 1,
                createdBy,
                createdAt,
                Objects.requireNonNull(time, "发布时间不能为空"));
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public AssistantVersionId id() {
        return id;
    }

    public AssistantId assistantId() {
        return assistantId;
    }

    public int number() {
        return number;
    }

    public long chatDeploymentId() {
        return chatDeploymentId;
    }

    public long chatDeploymentRevision() {
        return chatDeploymentRevision;
    }

    public int chatDeploymentOutputLimit() {
        return chatDeploymentOutputLimit;
    }

    public long knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public long indexVersionId() {
        return indexVersionId;
    }

    public long indexVersionRevision() {
        return indexVersionRevision;
    }

    public long embeddingDeploymentId() {
        return embeddingDeploymentId;
    }

    public long embeddingDeploymentRevision() {
        return embeddingDeploymentRevision;
    }

    public int embeddingDimension() {
        return embeddingDimension;
    }

    public boolean usesGovernedModelConfigurations() {
        return governedChat != null;
    }

    public GovernedChatModelBinding governedChat() {
        return governedChat;
    }

    /**
     * 返回发布后不可修改的聊天候选顺序；历史部署版本没有受控路由。
     */
    public GovernedChatModelRoute governedChatRoute() {
        return governedChatRoute;
    }

    public GovernedEmbeddingModelBinding governedEmbedding() {
        return governedEmbedding;
    }

    public String systemInstruction() {
        return systemInstruction;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public int retrievalLimit() {
        return retrievalLimit;
    }

    public double minimumScore() {
        return minimumScore;
    }

    public CitationPolicy citationPolicy() {
        return citationPolicy;
    }

    public RefusalPolicy refusalPolicy() {
        return refusalPolicy;
    }

    public AssistantVersionStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public CreatorId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    private static String requireInstruction(String value) {
        Objects.requireNonNull(value, "系统指令不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_INSTRUCTION_CODE_POINTS) {
            throw new IllegalArgumentException("系统指令格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))
                    && value.charAt(index) != '\n'
                    && value.charAt(index) != '\t') {
                throw new IllegalArgumentException("系统指令不能包含非文本控制字符");
            }
        }
        return value;
    }
}
