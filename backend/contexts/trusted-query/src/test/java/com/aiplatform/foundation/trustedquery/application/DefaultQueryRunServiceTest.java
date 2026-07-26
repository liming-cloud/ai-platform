package com.aiplatform.foundation.trustedquery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.api.model.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateQuery;
import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.api.model.ChatChunk;
import com.aiplatform.foundation.modelruntime.api.model.ChatRequest;
import com.aiplatform.foundation.modelruntime.api.model.ChatResult;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingVector;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class DefaultQueryRunServiceTest {

    private static final TenantId TENANT =
            new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final UUID ASSISTANT_VERSION =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CHAT =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID EMBEDDING =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SPACE =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID INDEX =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");

    @Test
    void 使用冻结索引和模型生成带引用回答且重放不重复调用() {
        var repository = new MemoryRepository();
        var models = new RecordingModels();
        var retrieval = new RecordingRetrieval(List.of(candidate(0.9)));
        var service = service(repository, models, retrieval);
        var command = new CreateQueryRunCommand(
                context("assistant:query", "knowledge:read"),
                "query-key-001",
                ASSISTANT_VERSION,
                "牛奶在哪里");

        var first = service.create(command);
        var replayed = service.create(command);

        assertEquals("ANSWERED", first.queryRun().status());
        assertTrue(first.queryRun().answer().contains("引用：[1]"));
        assertEquals(1, first.queryRun().citations().size());
        assertEquals(15, first.queryRun().usage().totalTokens());
        assertEquals(INDEX, retrieval.query.indexVersionId());
        assertEquals(EMBEDDING, retrieval.query.embeddingDeploymentId());
        assertEquals(256, models.chatRequest.maxOutputTokens());
        assertEquals(1, models.chatCalls);
        assertTrue(replayed.replayed());
        assertEquals(first.queryRun().id(), replayed.queryRun().id());
    }

    @Test
    void 证据低于阈值时拒答且不调用对话模型() {
        var repository = new MemoryRepository();
        var models = new RecordingModels();
        var service = service(
                repository,
                models,
                new RecordingRetrieval(List.of(candidate(0.1))));

        var result = service.create(new CreateQueryRunCommand(
                context("assistant:query", "knowledge:read"),
                "query-key-002",
                ASSISTANT_VERSION,
                "不存在的信息"));

        assertEquals("REFUSED", result.queryRun().status());
        assertEquals(
                "INSUFFICIENT_EVIDENCE",
                result.queryRun().refusalReason());
        assertEquals(0, models.chatCalls);
    }

    @Test
    void 缺少查询权限时不能读取助手或调用模型() {
        var repository = new MemoryRepository();
        var models = new RecordingModels();
        var service = service(
                repository,
                models,
                new RecordingRetrieval(List.of(candidate(1.0))));

        assertThrows(
                CandidateSearchPermissionDeniedException.class,
                () -> service.create(new CreateQueryRunCommand(
                        context("knowledge:read"),
                        "query-key-003",
                        ASSISTANT_VERSION,
                        "牛奶在哪里")));
        assertEquals(0, models.chatCalls);
        assertEquals(null, repository.current);
    }

    private static DefaultQueryRunService service(
            QueryRunRepository repository,
            RecordingModels models,
            RecordingRetrieval retrieval) {
        return new DefaultQueryRunService(
                (tenantId, assistantVersionId) ->
                        tenantId.equals(TENANT)
                                        && assistantVersionId.equals(ASSISTANT_VERSION)
                                ? Optional.of(snapshot())
                                : Optional.empty(),
                models,
                retrieval::retrieve,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> UUID.fromString(
                        "70000000-0000-0000-0000-000000000001"));
    }

    private static PublishedAssistantVersionSnapshot snapshot() {
        return new PublishedAssistantVersionSnapshot(
                ASSISTANT_VERSION,
                UUID.randomUUID(),
                1,
                CHAT,
                7,
                SPACE,
                INDEX,
                5,
                EMBEDDING,
                3,
                4,
                "只依据授权证据回答。",
                256,
                5,
                0.5,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE);
    }

    private static IndexCandidate candidate(double score) {
        return new IndexCandidate(
                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                1,
                "a".repeat(64),
                0,
                "牛奶位于一层冷藏区，价格为十二元。",
                score);
    }

    private static TrustedRequestContext context(String... permissions) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(UUID.randomUUID()),
                new MembershipId(UUID.randomUUID()),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                java.util.Arrays.stream(permissions)
                        .map(PermissionCode::new)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                "trace-query-001");
    }

    private static final class RecordingRetrieval {

        private final List<IndexCandidate> result;
        private IndexCandidateQuery query;

        private RecordingRetrieval(List<IndexCandidate> result) {
            this.result = result;
        }

        private List<IndexCandidate> retrieve(IndexCandidateQuery value) {
            query = value;
            return result;
        }
    }

    private static final class RecordingModels implements ModelRuntimeResolver {

        private int chatCalls;
        private ChatRequest chatRequest;

        @Override
        public ChatModelPort resolveChat(TenantId tenantId, UUID deploymentId) {
            return new ChatModelPort() {
                @Override
                public ChatResult chat(ChatRequest request) {
                    chatCalls++;
                    chatRequest = request;
                    return new ChatResult(
                            request.deployment(),
                            "牛奶位于一层冷藏区。",
                            "STOP",
                            new ModelUsage(10, 5, 15));
                }

                @Override
                public Flow.Publisher<ChatChunk> stream(ChatRequest request) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort
                resolveEmbedding(TenantId tenantId, UUID deploymentId) {
            return request -> new EmbeddingBatch(
                    request.deployment(),
                    List.of(new EmbeddingVector(
                            0,
                            new float[] {1, 0, 0, 0})));
        }

        @Override
        public void evictProvider(TenantId tenantId, UUID providerConnectionId) {
        }

        @Override
        public void evictDeployment(TenantId tenantId, UUID deploymentId) {
        }
    }

    private static final class MemoryRepository implements QueryRunRepository {

        private QueryRun current;
        private String key;
        private String fingerprint;

        @Override
        public QueryRunClaim claim(
                QueryRun queryRun,
                String idempotencyKey,
                String requestFingerprint) {
            if (current == null) {
                current = queryRun;
                key = idempotencyKey;
                fingerprint = requestFingerprint;
                return new QueryRunClaim(current, QueryRunClaimState.CLAIMED);
            }
            assertEquals(key, idempotencyKey);
            assertEquals(fingerprint, requestFingerprint);
            return new QueryRunClaim(
                    current,
                    current.status()
                                    == com.aiplatform.foundation.trustedquery.domain
                                            .QueryRunStatus.RUNNING
                            ? QueryRunClaimState.IN_PROGRESS
                            : QueryRunClaimState.REPLAYED);
        }

        @Override
        public void complete(QueryRun queryRun, long expectedVersion) {
            assertNotNull(current);
            assertEquals(current.version(), expectedVersion);
            current = queryRun;
        }

        @Override
        public Optional<QueryRun> find(TenantId tenantId, UUID queryRunId) {
            if (current != null
                    && current.tenantId().equals(tenantId)
                    && current.id().equals(queryRunId)) {
                return Optional.of(current);
            }
            return Optional.empty();
        }

        @Override
        public List<com.aiplatform.foundation.trustedquery.domain.QueryRunEvent> findEvents(
                TenantId tenantId,
                UUID queryRunId,
                long afterSequence,
                int limit) {
            return List.of();
        }
    }
}
