package com.aiplatform.foundation.trustedquery.application.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.api.model.index.ActiveIndexSnapshot;
import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingVector;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchQuery;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultCandidateSearchServiceTest {

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
        var service = new DefaultCandidateSearchService(
                activeIndex(),
                models(request -> new EmbeddingBatch(
                                request.deployment(),
                                List.of(new EmbeddingVector(
                                        0,
                                        new float[] {1, 0, 0, 0})))),
                retriever);

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
        var service = new DefaultCandidateSearchService(
                activeIndex(),
                models(request -> {
                    throw new AssertionError("无权限请求不能调用嵌入模型");
                }),
                retriever);

        assertThrows(
                CandidateSearchPermissionDeniedException.class,
                () -> service.search(new CandidateSearchQuery(
                        context("knowledge:write"),
                        Long.toString(SPACE),
                        "一层有什么商品",
                        5)));
        assertEquals(null, retriever.query);
    }

    private static ActiveIndexLookup activeIndex() {
        return (tenantId, knowledgeSpaceId) ->
                TENANT.equals(tenantId) && SPACE == knowledgeSpaceId
                        ? Optional.of(new ActiveIndexSnapshot(
                                SPACE,
                                INDEX,
                                2,
                                DEPLOYMENT.deploymentId(),
                                DEPLOYMENT.revision(),
                                DEPLOYMENT.dimension()))
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
            implements com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateRetriever {

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
