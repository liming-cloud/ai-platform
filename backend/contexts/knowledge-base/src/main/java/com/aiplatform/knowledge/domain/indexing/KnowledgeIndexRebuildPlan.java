package com.aiplatform.knowledge.domain.indexing;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一个知识库索引 generation 的冻结目标。模型与版本在创建后不可变。
 */
public record KnowledgeIndexRebuildPlan(
        TenantId tenantId,
        String rebuildId,
        String knowledgeBaseId,
        long generation,
        String targetModelConfigId,
        String targetEmbeddingModel,
        String indexVersion,
        KnowledgeIndexRebuildStatus status,
        Instant membersFrozenAt,
        Instant createdAt,
        String operatorId
) {
    public KnowledgeIndexRebuildPlan {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        rebuildId = requireText(rebuildId, "重建计划 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        if (generation < 1) {
            throw new IllegalArgumentException("索引 generation 必须大于 0");
        }
        targetModelConfigId = requireText(targetModelConfigId, "目标模型配置 ID");
        targetEmbeddingModel = requireText(targetEmbeddingModel, "目标 Embedding 模型");
        indexVersion = requireText(indexVersion, "索引版本");
        Objects.requireNonNull(status, "重建状态不能为空");
        Objects.requireNonNull(membersFrozenAt, "成员冻结时间不能为空");
        Objects.requireNonNull(createdAt, "重建创建时间不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
    }

    public static KnowledgeIndexRebuildPlan active(
            TenantId tenantId,
            String rebuildId,
            String knowledgeBaseId,
            long generation,
            String targetModelConfigId,
            String targetEmbeddingModel,
            Instant now,
            String operatorId
    ) {
        return new KnowledgeIndexRebuildPlan(
                tenantId,
                rebuildId,
                knowledgeBaseId,
                generation,
                targetModelConfigId,
                targetEmbeddingModel,
                "kb-%s-g-%d".formatted(knowledgeBaseId, generation),
                KnowledgeIndexRebuildStatus.ACTIVE,
                now,
                now,
                operatorId
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
