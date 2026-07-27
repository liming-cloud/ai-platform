package com.aiplatform.foundation.knowledgedata.api.extension;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * 在指定租户、知识空间和嵌入修订内执行的候选检索请求。
 */
public final class IndexCandidateQuery {

    private final TenantId tenantId;
    private final UUID knowledgeSpaceId;
    private final UUID indexVersionId;
    private final UUID embeddingDeploymentId;
    private final long embeddingRevision;
    private final float[] embedding;
    private final int limit;

    public IndexCandidateQuery(
            TenantId tenantId,
            UUID knowledgeSpaceId,
            UUID embeddingDeploymentId,
            long embeddingRevision,
            float[] embedding,
            int limit) {
        this(
                tenantId,
                knowledgeSpaceId,
                null,
                embeddingDeploymentId,
                embeddingRevision,
                embedding,
                limit);
    }

    /**
     * 创建绑定不可变索引版本的候选查询。
     */
    public IndexCandidateQuery(
            TenantId tenantId,
            UUID knowledgeSpaceId,
            UUID indexVersionId,
            UUID embeddingDeploymentId,
            long embeddingRevision,
            float[] embedding,
            int limit) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(
                knowledgeSpaceId,
                "知识空间标识不能为空");
        this.indexVersionId = indexVersionId;
        this.embeddingDeploymentId = Objects.requireNonNull(
                embeddingDeploymentId,
                "嵌入部署标识不能为空");
        if (embeddingRevision < 1) {
            throw new IllegalArgumentException("嵌入部署修订必须大于零");
        }
        this.embeddingRevision = embeddingRevision;
        Objects.requireNonNull(embedding, "查询向量不能为空");
        if (embedding.length < 1 || embedding.length > 2000) {
            throw new IllegalArgumentException("查询向量维度超出平台范围");
        }
        for (var value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("查询向量只能包含有限数值");
            }
        }
        this.embedding = embedding.clone();
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("候选数量必须在 1 到 100 之间");
        }
        this.limit = limit;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public UUID indexVersionId() {
        return indexVersionId;
    }

    public UUID embeddingDeploymentId() {
        return embeddingDeploymentId;
    }

    public long embeddingRevision() {
        return embeddingRevision;
    }

    public int embeddingDimension() {
        return embedding.length;
    }

    public float[] embedding() {
        return embedding.clone();
    }

    public int limit() {
        return limit;
    }
}
