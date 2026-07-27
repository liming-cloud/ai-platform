package com.aiplatform.foundation.knowledgedata.domain.indexversion.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 冻结来源快照并登记构建作业所需的完整事实。
 */
public record IndexVersionBuildRequest(
        TenantId tenantId,
        KnowledgeSpaceId knowledgeSpaceId,
        IndexVersionId indexVersionId,
        EmbeddingConfiguration embedding,
        String chunkPolicy,
        CreatorId createdBy,
        Instant createdAt,
        String idempotencyKey,
        String requestFingerprint) {

    private static final Pattern POLICY = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public IndexVersionBuildRequest {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(indexVersionId, "索引版本标识不能为空");
        Objects.requireNonNull(embedding, "嵌入配置不能为空");
        Objects.requireNonNull(chunkPolicy, "分块策略不能为空");
        if (!POLICY.matcher(chunkPolicy).matches()) {
            throw new IllegalArgumentException("分块策略格式不正确");
        }
        Objects.requireNonNull(createdBy, "创建成员不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        if (!SHA256.matcher(requestFingerprint).matches()) {
            throw new IllegalArgumentException("请求指纹格式不正确");
        }
    }
}
