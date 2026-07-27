package com.aiplatform.foundation.trustedquery.application;

import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateRetriever;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingRequest;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.trustedquery.api.CandidateSearchUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchQuery;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchResult;
import com.aiplatform.foundation.trustedquery.api.model.CandidateView;
import java.util.List;
import java.util.Objects;

/**
 * 先校验权限和可信租户，再嵌入查询并执行严格过滤候选检索。
 */
public final class DefaultCandidateSearchService implements CandidateSearchUseCase {

    private static final PermissionCode READ = new PermissionCode("knowledge:read");

    private final EmbeddingModelPort embeddingModel;
    private final EmbeddingDeploymentRef deployment;
    private final IndexCandidateRetriever candidates;

    public DefaultCandidateSearchService(
            EmbeddingModelPort embeddingModel,
            EmbeddingDeploymentRef deployment,
            IndexCandidateRetriever candidates) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "嵌入模型不能为空");
        this.deployment = Objects.requireNonNull(deployment, "嵌入部署不能为空");
        this.candidates = Objects.requireNonNull(candidates, "候选检索端口不能为空");
    }

    @Override
    public CandidateSearchResult search(CandidateSearchQuery query) {
        Objects.requireNonNull(query, "候选检索查询不能为空");
        if (!query.context().hasPermission(READ)) {
            throw new CandidateSearchPermissionDeniedException();
        }
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
                parsePositiveIdentifier(query.knowledgeSpaceId()),
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
