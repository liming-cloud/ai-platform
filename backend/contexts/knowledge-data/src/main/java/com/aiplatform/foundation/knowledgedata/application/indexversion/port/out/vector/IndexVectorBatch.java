package com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector;

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
        long embeddingDeploymentId,
        long embeddingRevision,
        int embeddingDimension,
        List<IndexVector> vectors) {

    public IndexVectorBatch {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (knowledgeSpaceId <= 0 || indexVersionId <= 0) {
            throw new IllegalArgumentException("知识空间标识和索引版本标识必须是正整数");
        }
        if (embeddingDeploymentId <= 0) {
            throw new IllegalArgumentException("嵌入部署标识必须是正整数");
        }
        if (embeddingRevision < 1) {
            throw new IllegalArgumentException("嵌入部署修订必须大于零");
        }
        if (embeddingDimension < 1 || embeddingDimension > 2000) {
            throw new IllegalArgumentException("嵌入维度超出平台范围");
        }
        Objects.requireNonNull(vectors, "索引向量不能为空");
        vectors = List.copyOf(vectors);
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("索引向量不能为空");
        }
        for (var vector : vectors) {
            Objects.requireNonNull(vector, "索引向量不能包含空值");
            if (vector.dimension() != embeddingDimension) {
                throw new IllegalArgumentException("索引向量维度不一致");
            }
        }
    }
}
