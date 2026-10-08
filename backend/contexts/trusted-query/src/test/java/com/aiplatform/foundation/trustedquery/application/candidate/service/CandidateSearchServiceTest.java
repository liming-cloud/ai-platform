package com.aiplatform.foundation.trustedquery.application.candidate.service;

import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchTargetNotFoundException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexSnapshot;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.EmbeddingConfiguration;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingVector;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.candidate.query.CandidateSearchQuery;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateSearchServiceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final long SPACE = 210000000000000001L;
    private static final long INDEX = 230000000000000001L;
    private static final EmbeddingDeploymentRef DEPLOYMENT =
            new EmbeddingDeploymentRef(
                    320000000000000001L,
                    1,
                    4);

    @Test
    void 可信租户和冻结嵌入修订必须进入候选检索端口() {
        var retriever = new RecordingRetriever();
        var service = new CandidateSearchService(
                knowledgeSpace(true),
                activeIndex(),
                models(request -> new EmbeddingBatch(
                                request.deployment(),
                                List.of(new EmbeddingVector(
                                        0,
                                        new float[] {1, 0, 0, 0})))),
                retriever, com.aiplatform.foundation.trustedquery.application.run.service.QueryAuthorizationFixture.allowed());

        var result = service.search(new CandidateSearchQuery(
                context("knowledge:read"),
                Long.toString(SPACE),
                "一层有什么商品",
                5));

        assertEquals(1, result.candidates().size());
        assertEquals(TENANT, retriever.query.tenantId());
        assertEquals(SPACE, retriever.query.knowledgeSpaceId());
        assertEquals(INDEX, retriever.query.indexVersionId());
        assertEquals(DEPLOYMENT.deploymentId(), retriever.query.embeddingDeploymentId());
        assertEquals(DEPLOYMENT.revision(), retriever.query.embeddingRevision());
    }

    @Test
    void 缺少读取权限时不调用嵌入和检索外部依赖() {
        var retriever = new RecordingRetriever();
        var service = new CandidateSearchService(
                knowledgeSpace(true),
                activeIndex(),
                models(request -> {
                    throw new AssertionError("无权限请求不能调用嵌入模型");
                }),
                retriever, com.aiplatform.foundation.trustedquery.application.run.service.QueryAuthorizationFixture.allowed());

        assertThrows(
                CandidateSearchPermissionDeniedException.class,
                () -> service.search(new CandidateSearchQuery(
                        context("knowledge:write"),
                        Long.toString(SPACE),
                        "一层有什么商品",
                        5)));
        assertEquals(null, retriever.query);
    }

    @Test
    void 受控活动索引必须重验嵌入配置且按配置快照检索() {
        var retriever = new RecordingRetriever();
        var configurationId = 320000000000000002L;
        var index = (ActiveIndexLookup) (tenantId, knowledgeSpaceId) -> Optional.of(
                new ActiveIndexSnapshot(
                        SPACE, INDEX, 2,
                        EmbeddingConfiguration.governed(
                                configurationId, 3, 310000000000000001L, 4, 4)));
        UsableModelConfigurationLookup configurations = (context, id, type) -> Optional.of(
                new UsableModelConfigurationSnapshot(
                        id, 3, new ControlledModelCatalogEntryId(310000000000000001L), 4,
                        ModelType.EMBEDDING, ModelRuntimeCapabilities.embedding(8192, 4)));
        GovernedEmbeddingRuntime embeddings = new GovernedEmbeddingRuntime() {
            @Override
            public EmbeddingBatch embed(
                    TrustedRequestContext context,
                    UsableModelConfigurationSnapshot expected,
                    com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest request) {
                return new EmbeddingBatch(request.deployment(),
                        List.of(new EmbeddingVector(0, new float[] {1, 0, 0, 0})));
            }

            @Override
            public EmbeddingBatch embed(
                    com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationUsageContext context,
                    UsableModelConfigurationSnapshot expected,
                    com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest request) {
                throw new UnsupportedOperationException();
            }
        };
        var service = new CandidateSearchService(
                knowledgeSpace(true), index, models(request -> {
                    throw new AssertionError("受控索引不得回退到旧部署运行时");
                }), configurations, embeddings, retriever, com.aiplatform.foundation.trustedquery.application.run.service.QueryAuthorizationFixture.allowed());

        service.search(new CandidateSearchQuery(
                context("knowledge:read"), Long.toString(SPACE), "一层有什么商品", 5));

        assertEquals(configurationId, retriever.query.embeddingConfiguration().configurationId());
        assertEquals(3, retriever.query.embeddingConfiguration().configurationRevision());
    }

    @Test
    void 当前租户不可见的知识空间必须按资源不存在处理且不调用模型() {
        var retriever = new RecordingRetriever();
        var service = new CandidateSearchService(
                knowledgeSpace(false),
                activeIndex(),
                models(request -> {
                    throw new AssertionError("不可见资源不能调用嵌入模型");
                }),
                retriever, com.aiplatform.foundation.trustedquery.application.run.service.QueryAuthorizationFixture.allowed());

        assertThrows(
                CandidateSearchTargetNotFoundException.class,
                () -> service.search(new CandidateSearchQuery(
                        context("knowledge:read"),
                        Long.toString(SPACE),
                        "一层有什么商品",
                        5)));
        assertEquals(null, retriever.query);
    }

    private static com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceLookup
            knowledgeSpace(boolean exists) {
        return (tenantId, knowledgeSpaceId) ->
                exists && TENANT.equals(tenantId) && SPACE == knowledgeSpaceId;
    }

    private static ActiveIndexLookup activeIndex() {
        return (tenantId, knowledgeSpaceId) ->
                TENANT.equals(tenantId) && SPACE == knowledgeSpaceId
                        ? Optional.of(new ActiveIndexSnapshot(
                                SPACE,
                                INDEX,
                                2,
                                new EmbeddingConfiguration(
                                        DEPLOYMENT.deploymentId(),
                                        DEPLOYMENT.revision(),
                                        DEPLOYMENT.dimension())))
                        : Optional.empty();
    }

    private static ModelRuntimeResolver models(EmbeddingModelPort embedding) {
        return new ModelRuntimeResolver() {

            @Override
            public ChatModelPort resolveChat(TenantId tenantId, long deploymentId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EmbeddingModelPort resolveEmbedding(
                    TenantId tenantId,
                    long deploymentId) {
                assertEquals(TENANT, tenantId);
                assertEquals(DEPLOYMENT.deploymentId(), deploymentId);
                return embedding;
            }

            @Override
            public void evictProvider(TenantId tenantId, long providerConnectionId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void evictDeployment(TenantId tenantId, long deploymentId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(1843651857942052866L),
                new MembershipId(304564822427566082L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-candidate-001");
    }

    private static final class RecordingRetriever
            implements com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateRetriever {

        private IndexCandidateQuery query;

        @Override
        public List<IndexCandidate> retrieve(IndexCandidateQuery value) {
            query = value;
            return List.of(new IndexCandidate(
                    220000000000000001L,
                    1,
                    "a".repeat(64),
                    0,
                    "一层商品",
                    1.0));
        }
    }
}
