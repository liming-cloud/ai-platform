package com.aiplatform.knowledge.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库聚合根，管理知识可见性、引用要求和发布状态。
 */
public record KnowledgeBase(
        TenantId tenantId,
        String knowledgeBaseId,
        String name,
        String description,
        KnowledgeVisibility visibility,
        Set<String> allowedPrincipalIds,
        String createdBy,
        String embeddingModelConfigId,
        boolean citationRequired,
        boolean published,
        Instant createdAt,
        Instant updatedAt
) implements AggregateRoot<String> {
    public KnowledgeBase {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        name = requireText(name, "知识库名称");
        description = Objects.requireNonNull(description, "知识库描述不能为空").trim();
        Objects.requireNonNull(visibility, "知识库可见性不能为空");
        allowedPrincipalIds = normalizePrincipalIds(allowedPrincipalIds);
        createdBy = requireText(createdBy, "知识库创建人");
        embeddingModelConfigId = requireText(embeddingModelConfigId, "知识库向量模型配置 ID");
        Objects.requireNonNull(createdAt, "知识库创建时间不能为空");
        Objects.requireNonNull(updatedAt, "知识库更新时间不能为空");
    }

    public boolean requiresPrincipalGrant() {
        return visibility == KnowledgeVisibility.SPECIFIED_MEMBERS
                || visibility == KnowledgeVisibility.SPECIFIED_ROLES;
    }

    public boolean canBeRetrievedBy(String principalId) {
        String normalizedPrincipalId = requirePrincipal(principalId);
        if (!published) {
            return false;
        }
        if (visibility == KnowledgeVisibility.PUBLIC) {
            return true;
        }
        return allowedPrincipalIds.contains(normalizedPrincipalId);
    }

    public List<String> retrievalAclTokensFor(String principalId) {
        String normalizedPrincipalId = requirePrincipal(principalId);
        if (!published) {
            return List.of();
        }
        if (visibility == KnowledgeVisibility.PUBLIC || allowedPrincipalIds.contains(normalizedPrincipalId)) {
            return List.of("kb:" + knowledgeBaseId);
        }
        return List.of();
    }

    @Override
    public String id() {
        return knowledgeBaseId;
    }

    /**
     * 切换知识库挂载向量模型。应用服务必须先完成新模型运行时探活，再调用该领域行为并触发重索引。
     */
    public KnowledgeBase switchEmbeddingModel(String nextEmbeddingModelConfigId, Instant updatedAt) {
        return new KnowledgeBase(
                tenantId,
                knowledgeBaseId,
                name,
                description,
                visibility,
                allowedPrincipalIds,
                createdBy,
                requireText(nextEmbeddingModelConfigId, "知识库向量模型配置 ID"),
                citationRequired,
                published,
                createdAt,
                Objects.requireNonNull(updatedAt, "知识库更新时间不能为空")
        );
    }

    public KnowledgeBase withSettings(
            String nextName,
            String nextDescription,
            KnowledgeVisibility nextVisibility,
            String nextEmbeddingModelConfigId,
            Instant updatedAt
    ) {
        return new KnowledgeBase(
                tenantId,
                knowledgeBaseId,
                nextName,
                nextDescription,
                Objects.requireNonNull(nextVisibility, "知识库可见性不能为空"),
                allowedPrincipalIds,
                createdBy,
                requireText(nextEmbeddingModelConfigId, "知识库向量模型配置 ID"),
                citationRequired,
                published,
                createdAt,
                Objects.requireNonNull(updatedAt, "知识库更新时间不能为空")
        );
    }

    public KnowledgeBase disable(Instant updatedAt) {
        return new KnowledgeBase(
                tenantId,
                knowledgeBaseId,
                name,
                description,
                visibility,
                allowedPrincipalIds,
                createdBy,
                embeddingModelConfigId,
                citationRequired,
                false,
                createdAt,
                Objects.requireNonNull(updatedAt, "知识库更新时间不能为空")
        );
    }

    private static Set<String> normalizePrincipalIds(Set<String> principalIds) {
        Objects.requireNonNull(principalIds, "授权主体不能为空");
        Set<String> normalized = new LinkedHashSet<>();
        for (String principalId : principalIds) {
            String normalizedPrincipalId = requireText(principalId, "授权主体");
            if (!normalized.add(normalizedPrincipalId)) {
                throw new IllegalArgumentException("授权主体重复：" + normalizedPrincipalId);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String requirePrincipal(String principalId) {
        return requireText(principalId, "访问主体");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
