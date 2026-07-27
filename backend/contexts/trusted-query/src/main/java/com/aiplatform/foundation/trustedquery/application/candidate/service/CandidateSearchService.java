package com.aiplatform.foundation.trustedquery.application.candidate.service;

import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchIndexUnavailableException;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.candidate.query.CandidateSearchQuery;
import com.aiplatform.foundation.trustedquery.application.candidate.dto.CandidateSearchResult;
import com.aiplatform.foundation.trustedquery.application.candidate.dto.CandidateView;
import java.util.List;
import java.util.Objects;

/**
 * 先校验权限和可信租户，再嵌入查询并执行严格过滤候选检索。
 */
public final class CandidateSearchService  {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");

    private final ActiveIndexLookup indexes;
    private final ModelRuntimeResolver models;
    private final IndexCandidateRetriever candidates;

    public CandidateSearchService(
            ActiveIndexLookup indexes,
            ModelRuntimeResolver models,
            IndexCandidateRetriever candidates) {
        this.indexes = Objects.requireNonNull(indexes, "活动索引查询不能为空");
        this.models = Objects.requireNonNull(models, "模型运行时解析器不能为空");
        this.candidates = Objects.requireNonNull(candidates, "候选检索端口不能为空");
    }

    public CandidateSearchResult search(CandidateSearchQuery query) {
        Objects.requireNonNull(query, "候选检索查询不能为空");
        if (!query.context().hasPermission(READ)) {
            throw new CandidateSearchPermissionDeniedException();
        }
        var knowledgeSpaceId = parsePositiveIdentifier(query.knowledgeSpaceId());
        var index = indexes
                .find(query.context().tenantId(), knowledgeSpaceId)
                .orElseThrow(CandidateSearchIndexUnavailableException::new);
        var deployment = new EmbeddingDeploymentRef(
                index.embeddingDeploymentId(),
                index.embeddingDeploymentRevision(),
                index.embeddingDimension());
        var embeddingModel = models.resolveEmbedding(
                query.context().tenantId(),
                index.embeddingDeploymentId());
        var embedded = embeddingModel.embed(new EmbeddingRequest(
                deployment,
                List.of(query.text())));
        if (!deployment.equals(embedded.deployment())
                || embedded.vectors().size() != 1
                || embedded.vectors().getFirst().index() != 0) {
            throw new IllegalStateException("查询嵌入结果不符合冻结部署");
        }
        var found = candidates.retrieve(new IndexCandidateQuery(
                query.context().tenantId(),
                knowledgeSpaceId,
                index.indexVersionId(),
                deployment.deploymentId(),
                deployment.revision(),
                embedded.vectors().getFirst().values(),
                query.limit()));
        return new CandidateSearchResult(found.stream()
                .map(value -> new CandidateView(
                        Long.toString(value.dataSourceId()),
                        value.contentVersion(),
                        value.chunkId(),
                        value.ordinal(),
                        value.content(),
                        value.score()))
                .toList());
    }

    private static long parsePositiveIdentifier(String value) {
        try {
            var identifier = Long.parseLong(value);
            if (identifier <= 0) {
                throw new IllegalArgumentException("知识空间标识必须是正整数");
            }
            return identifier;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("知识空间标识格式不正确", exception);
        }
    }
}
