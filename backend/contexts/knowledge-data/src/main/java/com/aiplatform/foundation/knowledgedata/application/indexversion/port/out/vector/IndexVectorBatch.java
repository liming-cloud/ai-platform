package com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * 单个索引版本一次性提交给向量存储的完整产物。
 */
public record IndexVectorBatch(
        TenantId tenantId,
        long knowledgeSpaceId,
        long indexVersionId,
        EmbeddingConfiguration embedding,
        List<IndexVector> vectors) {

    public IndexVectorBatch {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (knowledgeSpaceId <= 0 || indexVersionId <= 0) {
            throw new IllegalArgumentException("知识空间标识和索引版本标识必须是正整数");
        }
        embedding = Objects.requireNonNull(embedding, "嵌入配置不能为空");
        Objects.requireNonNull(vectors, "索引向量不能为空");
        vectors = List.copyOf(vectors);
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("索引向量不能为空");
        }
        for (var vector : vectors) {
            Objects.requireNonNull(vector, "索引向量不能包含空值");
            if (vector.dimension() != embedding.dimension()) {
                throw new IllegalArgumentException("索引向量维度不一致");
            }
        }
    }

    /**
     * 兼容 M1 已发布部署构建产物；新索引必须使用完整的受控配置绑定。
     */
    public IndexVectorBatch(
            TenantId tenantId,
            long knowledgeSpaceId,
            long indexVersionId,
            long embeddingDeploymentId,
            long embeddingRevision,
            int embeddingDimension,
            List<IndexVector> vectors) {
        this(
                tenantId,
                knowledgeSpaceId,
                indexVersionId,
                new EmbeddingConfiguration(
                        embeddingDeploymentId,
                        embeddingRevision,
                        embeddingDimension),
                vectors);
    }

    public long embeddingDeploymentId() {
        return embedding.deploymentId();
    }

    public long embeddingRevision() {
        return embedding.deploymentRevision();
    }

    public int embeddingDimension() {
        return embedding.dimension();
    }
}
