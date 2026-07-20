package com.aiplatform.knowledge.domain.port.retrieval.model;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 检索仓储查询条件，承载数据库前置过滤和 pgvector 查询向量。
 */
public record RetrievalSearchQuery(
        TenantId tenantId,
        List<String> knowledgeBaseIds,
        String query,
        float[] queryEmbedding,
        String embeddingModel,
        String indexVersion,
        List<String> aclTokens,
        Instant visibleAt,
        int topK,
        double scoreThreshold
) {
    public RetrievalSearchQuery {
        Objects.requireNonNull(tenantId, "租户上下文不能为空");
        knowledgeBaseIds = normalizeRequired(knowledgeBaseIds, "知识库范围");
        query = requireText(query, "检索问题");
        Objects.requireNonNull(queryEmbedding, "查询向量不能为空");
        queryEmbedding = normalizeEmbedding(queryEmbedding);
        embeddingModel = requireText(embeddingModel, "Embedding 模型");
        indexVersion = requireText(indexVersion, "索引版本");
        aclTokens = normalizeRequired(aclTokens, "权限快照");
        Objects.requireNonNull(visibleAt, "可见性校验时间不能为空");
        if (topK < 1) {
            throw new IllegalArgumentException("召回数量必须为正数");
        }
        if (!Double.isFinite(scoreThreshold) || scoreThreshold < 0 || scoreThreshold > 1) {
            throw new IllegalArgumentException("相似度阈值必须在 0 到 1 之间");
        }
    }

    @Override
    public float[] queryEmbedding() {
        return queryEmbedding.clone();
    }

    private static List<String> normalizeRequired(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        List<String> normalized = values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private static float[] normalizeEmbedding(float[] embedding) {
        if (embedding.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        float[] normalized = embedding.clone();
        for (float value : normalized) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("查询向量不能包含非有限数值");
            }
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
