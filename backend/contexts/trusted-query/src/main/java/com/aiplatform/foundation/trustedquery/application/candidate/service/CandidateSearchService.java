package com.aiplatform.foundation.trustedquery.application.candidate.service;

import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateRetriever;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.EmbeddingConfiguration;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchIndexUnavailableException;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchTargetNotFoundException;
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

    private final KnowledgeSpaceLookup knowledgeSpaces;
    private final ActiveIndexLookup indexes;
    private final ModelRuntimeResolver models;
    private final UsableModelConfigurationLookup modelConfigurations;
    private final GovernedEmbeddingRuntime governedEmbeddings;
    private final IndexCandidateRetriever candidates;
    private final com.aiplatform.foundation.trustedquery.application.run.service.QueryRunAuthorization authorization;

    public CandidateSearchService(
            KnowledgeSpaceLookup knowledgeSpaces,
            ActiveIndexLookup indexes,
            ModelRuntimeResolver models,
            IndexCandidateRetriever candidates,
            com.aiplatform.foundation.trustedquery.application.run.service.QueryRunAuthorization authorization) {
        this(knowledgeSpaces, indexes, models, null, null, candidates, authorization);
    }

    public CandidateSearchService(
            KnowledgeSpaceLookup knowledgeSpaces,
            ActiveIndexLookup indexes,
            ModelRuntimeResolver models,
            UsableModelConfigurationLookup modelConfigurations,
            GovernedEmbeddingRuntime governedEmbeddings,
            IndexCandidateRetriever candidates,
            com.aiplatform.foundation.trustedquery.application.run.service.QueryRunAuthorization authorization) {
        this.authorization = Objects.requireNonNull(authorization);
        this.knowledgeSpaces = Objects.requireNonNull(
                knowledgeSpaces,
                "知识空间查询不能为空");
        this.indexes = Objects.requireNonNull(indexes, "活动索引查询不能为空");
        this.models = Objects.requireNonNull(models, "模型运行时解析器不能为空");
        this.modelConfigurations = modelConfigurations;
        this.governedEmbeddings = governedEmbeddings;
        this.candidates = Objects.requireNonNull(candidates, "候选检索端口不能为空");
    }

    public CandidateSearchResult search(CandidateSearchQuery query) {
        Objects.requireNonNull(query, "候选检索查询不能为空");
        if (!query.context().hasPermission(READ)) {
            throw new CandidateSearchPermissionDeniedException();
        }
        authorization.requireAdministrativeSearch(query.context());
        var knowledgeSpaceId = parsePositiveIdentifier(query.knowledgeSpaceId());
        if (!knowledgeSpaces.exists(query.context().tenantId(), knowledgeSpaceId)) {
            throw new CandidateSearchTargetNotFoundException();
        }
        var index = indexes
                .find(query.context().tenantId(), knowledgeSpaceId)
                .orElseThrow(CandidateSearchIndexUnavailableException::new);
        if (index.embedding().isGoverned()) {
            return searchGoverned(query, knowledgeSpaceId, index);
        }
        var deployment = new EmbeddingDeploymentRef(
                index.embedding().deploymentId(),
                index.embedding().deploymentRevision(),
                index.embedding().dimension());
        var embeddingModel = models.resolveEmbedding(
                query.context().tenantId(),
                index.embedding().deploymentId());
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
        authorization.requireAdministrativeSearch(query.context());
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

    private CandidateSearchResult searchGoverned(
            CandidateSearchQuery query,
            long knowledgeSpaceId,
            com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexSnapshot index) {
        var configuration = requireEmbeddingConfiguration(query.context(), index.embedding());
        var deployment = new EmbeddingDeploymentRef(
                configuration.configurationId().value(),
                configuration.configurationRevision(),
                configuration.capabilities().embeddingDimension());
        var embedded = requireGovernedEmbeddings().embed(
                query.context(),
                configuration,
                new EmbeddingRequest(deployment, List.of(query.text())));
        if (!deployment.equals(embedded.deployment())
                || embedded.vectors().size() != 1
                || embedded.vectors().getFirst().index() != 0) {
            throw new IllegalStateException("查询嵌入结果不符合冻结配置");
        }
        var found = candidates.retrieve(new IndexCandidateQuery(
                query.context().tenantId(),
                knowledgeSpaceId,
                index.indexVersionId(),
                index.embedding(),
                embedded.vectors().getFirst().values(),
                query.limit()));
        authorization.requireAdministrativeSearch(query.context());
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

    private UsableModelConfigurationSnapshot requireEmbeddingConfiguration(
            com.aiplatform.foundation.security.identity.TrustedRequestContext context,
            EmbeddingConfiguration expected) {
        if (modelConfigurations == null || governedEmbeddings == null) {
            throw new CandidateSearchIndexUnavailableException();
        }
        return modelConfigurations.find(
                        context,
                        new ModelConfigurationId(expected.configurationId()),
                        ModelType.EMBEDDING)
                .filter(snapshot -> snapshot.configurationRevision()
                                == expected.configurationRevision()
                        && snapshot.catalogEntryId().value() == expected.catalogEntryId()
                        && snapshot.catalogEntryRevision()
                                == expected.catalogEntryRevision()
                        && snapshot.capabilities().embeddingDimension()
                                == expected.dimension())
                .orElseThrow(CandidateSearchIndexUnavailableException::new);
    }

    private GovernedEmbeddingRuntime requireGovernedEmbeddings() {
        if (governedEmbeddings == null) {
            throw new CandidateSearchIndexUnavailableException();
        }
        return governedEmbeddings;
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
