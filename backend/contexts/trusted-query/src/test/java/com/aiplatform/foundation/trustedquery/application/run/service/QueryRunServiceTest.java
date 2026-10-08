package com.aiplatform.foundation.trustedquery.application.run.service;

import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaLimitType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.RefusalPolicy;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidate;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.IndexCandidateQuery;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatInvocationTransientException;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedEmbeddingRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatChunk;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatResult;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingVector;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationSnapshot;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.run.command.CreateQueryRunCommand;
import com.aiplatform.foundation.assistantdelivery.application.answer.facade.AssistantTrustedAnswerExecutionException;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class QueryRunServiceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final long ASSISTANT_VERSION = 420000000000000001L;
    private static final long CHAT = 320000000000000001L;
    private static final long EMBEDDING = 320000000000000002L;
    private static final long SPACE = 210000000000000001L;
    private static final long INDEX = 230000000000000001L;
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
                Long.toString(ASSISTANT_VERSION),
                "牛奶在哪里");

        var first = service.create(command);
        var replayed = service.create(command);

        assertEquals("ANSWERED", first.queryRun().status());
        assertTrue(first.queryRun().answer().contains("引用：[1]"));
        assertEquals(1, first.queryRun().citations().size());
        assertEquals(15, first.queryRun().usage().totalTokens());
        assertEquals(INDEX, retrieval.query.indexVersionId());
        assertEquals(EMBEDDING, retrieval.query.embeddingDeploymentId());
        assertEquals(20, retrieval.query.limit());
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
                Long.toString(ASSISTANT_VERSION),
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
                        Long.toString(ASSISTANT_VERSION),
                        "牛奶在哪里")));
        assertEquals(0, models.chatCalls);
        assertEquals(null, repository.current);
    }

    @Test
    void 超出配额时不执行检索或模型调用() {
        var repository = new MemoryRepository();
        repository.rejectQuota = true;
        var models = new RecordingModels();
        var retrieval = new RecordingRetrieval(List.of(candidate(1.0)));
        var service = service(repository, models, retrieval);

        assertThrows(
                QueryQuotaExceededException.class,
                () -> service.create(new CreateQueryRunCommand(
                        context("assistant:query", "knowledge:read"),
                        "query-key-quota",
                        Long.toString(ASSISTANT_VERSION),
                        "牛奶在哪里")));

        assertEquals(null, retrieval.query);
        assertEquals(0, models.chatCalls);
    }

    @Test
    void 受控助手必须在配额占用后重验双配置且不回退到旧部署() {
        var repository = new MemoryRepository();
        var legacy = new RecordingModels();
        var retrieval = new RecordingRetrieval(List.of(candidate(0.9)));
        var embeddings = new RecordingGovernedEmbeddings(repository);
        var chats = new RecordingGovernedChats(repository);
        var service = new QueryRunService(
                (tenantId, assistantVersionId) -> Optional.of(governedSnapshot()),
                legacy,
                configurations(),
                embeddings,
                chats,
                retrieval::retrieve,
                repository,
                committer(repository),
                new QueryQuotaPolicy(100, 4, Duration.ofMinutes(2)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 430000000000000001L, QueryAuthorizationFixture.allowed());

        var result = service.create(new CreateQueryRunCommand(
                context("assistant:query", "knowledge:read"),
                "query-key-governed",
                Long.toString(ASSISTANT_VERSION),
                "牛奶在哪里"));

        assertEquals("ANSWERED", result.queryRun().status());
        assertEquals(1, embeddings.calls);
        assertEquals(1, chats.calls);
        assertEquals(0, legacy.chatCalls);
        assertTrue(retrieval.query.embeddingConfiguration().isGoverned());
        assertEquals(EMBEDDING, retrieval.query.embeddingConfiguration().configurationId());
    }

    @Test
    void 全部受控聊天候选瞬态失败时应将尝试轨迹写入失败终态() {
        var repository = new MemoryRepository();
        var chats = new RecordingGovernedChats(repository);
        chats.transientFailure = true;
        var service = new QueryRunService(
                (tenantId, assistantVersionId) -> Optional.of(governedSnapshot()),
                new RecordingModels(),
                configurations(),
                new RecordingGovernedEmbeddings(repository),
                chats,
                new RecordingRetrieval(List.of(candidate(0.9)))::retrieve,
                repository,
                committer(repository),
                new QueryQuotaPolicy(100, 4, Duration.ofMinutes(2)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 430000000000000001L, QueryAuthorizationFixture.allowed());

        assertThrows(AssistantTrustedAnswerExecutionException.class, () -> service.create(new CreateQueryRunCommand(
                context("assistant:query", "knowledge:read"),
                "query-key-governed-transient-failure",
                Long.toString(ASSISTANT_VERSION),
                "牛奶在哪里")));

        assertEquals("FAILED", repository.current.status().name());
        assertEquals(1, repository.terminal.modelInvocationTrace().attempts().size());
        assertEquals(
                com.aiplatform.foundation.assistantdelivery.application.answer.facade.ModelInvocationTrace.Outcome
                        .TRANSIENT_FAILURE,
                repository.terminal.modelInvocationTrace().attempts().getFirst().outcome());
    }

    @Test
    void 候选已经收集后终态映射失败仍要固化已收集失败事实() {
        var repository = new MemoryRepository();
        var models = new RecordingModels();
        models.answer = "a".repeat(40_000);
        var service = service(repository, models, new RecordingRetrieval(List.of(candidate(0.9))));

        assertThrows(
                AssistantTrustedAnswerExecutionException.class,
                () -> service.create(new CreateQueryRunCommand(
                        context("assistant:query", "knowledge:read"),
                        "query-key-terminal-failure",
                        Long.toString(ASSISTANT_VERSION),
                        "牛奶在哪里")));

        assertEquals("FAILED", repository.current.status().name());
        assertEquals(
                com.aiplatform.foundation.trustedquery.application.diagnostic.dto.CandidateCollection.COLLECTED,
                repository.terminal.diagnostic().candidateCollection());
        assertEquals(1, repository.terminal.diagnostic().candidateCount());
    }

    @Test
    void 同租户另一个成员不能读取本人的查询正文和引用() {
        var repository = new MemoryRepository();
        var service = service(repository, new RecordingModels(), new RecordingRetrieval(List.of(candidate(0.9))));
        var original = context("assistant:query", "knowledge:read");
        var created = service.create(new CreateQueryRunCommand(original, "owner-isolation", Long.toString(ASSISTANT_VERSION), "牛奶在哪里"));
        var other = new TrustedRequestContext(original.tenantId(), new SubjectId(7200000000000000099L),
                new MembershipId(7300000000000000099L), original.credentialType(), original.channel(),
                original.permissionVersion(), original.permissions(), "trace-other-member");
        assertThrows(CandidateSearchPermissionDeniedException.class,
                () -> service.get(new com.aiplatform.foundation.trustedquery.application.run.query.GetQueryRunQuery(other, created.queryRun().id())));
    }

    @Test
    void 未开放本人历史时在运行存在性查询之前拒绝() {
        var repository = org.mockito.Mockito.spy(new MemoryRepository());
        var access = org.mockito.Mockito.mock(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class);
        var actor = context("assistant:query", "knowledge:read");
        org.mockito.Mockito.doThrow(new CandidateSearchPermissionDeniedException()).when(access).requireFeature(actor, "ownHistory");
        var service = service(repository, new RecordingModels(), new RecordingRetrieval(List.of()), new QueryRunAuthorization(access));

        assertThrows(CandidateSearchPermissionDeniedException.class, () -> service.get(
                new com.aiplatform.foundation.trustedquery.application.run.query.GetQueryRunQuery(actor, "1")));
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void 历史和引用关闭仍可完成新对话但读取与重放必须拒绝() {
        var repository = new MemoryRepository();
        var access = org.mockito.Mockito.mock(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class);
        org.mockito.Mockito.when(access.read(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.aiplatform.foundation.organizationaccess.application.member.facade.MemberEffectiveAccess(false,
                        java.util.Map.of("conversation", true, "ownHistory", false, "authorizedCitations", false), java.util.Set.of(1L)));
        org.mockito.Mockito.doThrow(new CandidateSearchPermissionDeniedException()).when(access)
                .requireAssistantVersion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq("ownHistory"));
        var models = new RecordingModels();
        var service = service(repository, models, new RecordingRetrieval(List.of(candidate(0.9))), new QueryRunAuthorization(access));
        var command = new CreateQueryRunCommand(context("assistant:query", "knowledge:read"), "history-disabled", Long.toString(ASSISTANT_VERSION), "牛奶在哪里");
        var result = service.create(command);
        assertEquals("ANSWERED", result.queryRun().status());
        assertTrue(result.queryRun().citations().isEmpty());
        assertEquals(1, models.chatCalls);
        assertThrows(CandidateSearchPermissionDeniedException.class, () -> service.get(
                new com.aiplatform.foundation.trustedquery.application.run.query.GetQueryRunQuery(command.context(), result.queryRun().id())));
        assertThrows(CandidateSearchPermissionDeniedException.class, () -> service.create(command));
        assertEquals(1, models.chatCalls);
    }

    @Test
    void 模型期间撤权不得输出回答且提交失败终态() {
        var repository = new MemoryRepository();
        var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        var access = org.mockito.Mockito.mock(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (!allowed.get()) throw new CandidateSearchPermissionDeniedException();
            return null;
        }).when(access).requireAssistantVersion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        var models = new RecordingModels(); models.afterChat = () -> allowed.set(false);
        var service = service(repository, models, new RecordingRetrieval(List.of(candidate(0.9))), new QueryRunAuthorization(access));
        assertThrows(CandidateSearchPermissionDeniedException.class, () -> service.create(new CreateQueryRunCommand(
                context("assistant:query", "knowledge:read"), "revoked-during-model", Long.toString(ASSISTANT_VERSION), "牛奶在哪里")));
        assertEquals("FAILED", repository.current.status().name());
        assertNull(repository.current.answer());
        assertEquals(1, models.chatCalls);
    }

    private static QueryRunService service(
            QueryRunRepository repository,
            RecordingModels models,
            RecordingRetrieval retrieval) {
        return service(repository, models, retrieval, QueryAuthorizationFixture.allowed());
    }

    private static QueryRunService service(QueryRunRepository repository, RecordingModels models,
            RecordingRetrieval retrieval, QueryRunAuthorization authorization) {
        return new QueryRunService(
                (tenantId, assistantVersionId) ->
                        tenantId.equals(TENANT)
                                        && assistantVersionId == ASSISTANT_VERSION
                                ? Optional.of(snapshot())
                                : Optional.empty(),
                models,
                retrieval::retrieve,
                repository,
                committer((MemoryRepository) repository),
                new QueryQuotaPolicy(100, 4, Duration.ofMinutes(2)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 430000000000000001L, authorization);
    }

    private static com.aiplatform.foundation.trustedquery.application.diagnostic.port.out
            .QueryRunTerminalCommitter committer(MemoryRepository repository) {
        return terminal -> {
            assertNotNull(repository.current);
            assertEquals(repository.current.version(), terminal.expectedVersion());
            repository.current = terminal.queryRun();
            repository.terminal = terminal;
        };
    }

    private static PublishedAssistantVersionSnapshot snapshot() {
        return new PublishedAssistantVersionSnapshot(
                ASSISTANT_VERSION,
                410000000000000001L,
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

    private static PublishedAssistantVersionSnapshot governedSnapshot() {
        return PublishedAssistantVersionSnapshot.governed(
                ASSISTANT_VERSION,
                410000000000000001L,
                1,
                SPACE,
                INDEX,
                5,
                new GovernedChatModelBinding(CHAT, 7, 310000000000000001L, 6, 256),
                new GovernedEmbeddingModelBinding(
                        EMBEDDING, 3, 310000000000000002L, 4, 4),
                "只依据授权证据回答。",
                256,
                5,
                0.5,
                CitationPolicy.REQUIRED,
                RefusalPolicy.INSUFFICIENT_EVIDENCE);
    }

    private static UsableModelConfigurationLookup configurations() {
        return (context, id, type) -> {
            if (type == ModelType.CHAT && id.value() == CHAT) {
                return Optional.of(new UsableModelConfigurationSnapshot(
                        id, 7, new ControlledModelCatalogEntryId(310000000000000001L), 6,
                        type, ModelRuntimeCapabilities.chat(8192, 256)));
            }
            if (type == ModelType.EMBEDDING && id.value() == EMBEDDING) {
                return Optional.of(new UsableModelConfigurationSnapshot(
                        id, 3, new ControlledModelCatalogEntryId(310000000000000002L), 4,
                        type, ModelRuntimeCapabilities.embedding(8192, 4)));
            }
            return Optional.empty();
        };
    }

    private static IndexCandidate candidate(double score) {
        return new IndexCandidate(
                220000000000000001L,
                1,
                "a".repeat(64),
                0,
                "牛奶位于一层冷藏区，价格为十二元。",
                score);
    }

    private static TrustedRequestContext context(String... permissions) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(7200000000000000004L),
                new MembershipId(7300000000000000004L),
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
        private String answer = "牛奶位于一层冷藏区。";
        private Runnable afterChat = () -> {};

        @Override
        public ChatModelPort resolveChat(TenantId tenantId, long deploymentId) {
            return new ChatModelPort() {
                @Override
                public ChatResult chat(ChatRequest request) {
                    chatCalls++;
                    chatRequest = request;
                    afterChat.run();
                    return new ChatResult(
                            request.deployment(),
                            answer,
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
        public com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort
                resolveEmbedding(TenantId tenantId, long deploymentId) {
            return request -> new EmbeddingBatch(
                    request.deployment(),
                    List.of(new EmbeddingVector(
                            0,
                            new float[] {1, 0, 0, 0})));
        }

        @Override
        public void evictProvider(TenantId tenantId, long providerConnectionId) {
        }

        @Override
        public void evictDeployment(TenantId tenantId, long deploymentId) {
        }
    }

    private static final class RecordingGovernedEmbeddings implements GovernedEmbeddingRuntime {
        private final MemoryRepository repository;
        private int calls;

        private RecordingGovernedEmbeddings(MemoryRepository repository) {
            this.repository = repository;
        }

        @Override
        public EmbeddingBatch embed(
                TrustedRequestContext context,
                UsableModelConfigurationSnapshot expected,
                com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest request) {
            assertNotNull(repository.current);
            calls++;
            return new EmbeddingBatch(
                    request.deployment(), List.of(new EmbeddingVector(0, new float[] {1, 0, 0, 0})));
        }

        @Override
        public EmbeddingBatch embed(
                com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationUsageContext context,
                UsableModelConfigurationSnapshot expected,
                com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingGovernedChats implements GovernedChatRuntime {
        private final MemoryRepository repository;
        private int calls;
        private boolean transientFailure;

        private RecordingGovernedChats(MemoryRepository repository) {
            this.repository = repository;
        }

        @Override
        public ChatResult chat(
                TrustedRequestContext context,
                UsableModelConfigurationSnapshot expected,
                ChatRequest request) {
            assertNotNull(repository.current);
            calls++;
            if (transientFailure) {
                throw ChatInvocationTransientException.beforeOutput(new IllegalStateException("模型暂时不可用"));
            }
            return new ChatResult(
                    request.deployment(), "牛奶位于一层冷藏区。", "STOP",
                    new ModelUsage(10, 5, 15));
        }
    }

    private static final class MemoryRepository implements QueryRunRepository {

        private QueryRun current;
        private com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryRunTerminalCommit terminal;
        private String key;
        private String fingerprint;
        private boolean rejectQuota;

        @Override
        public QueryRunClaim claim(
                QueryRun queryRun,
                String idempotencyKey,
                String requestFingerprint,
                QueryQuotaPolicy quota) {
            if (rejectQuota) {
                throw new QueryQuotaExceededException(
                        QueryQuotaLimitType.DAILY,
                        quota.dailyQueryLimit(),
                        NOW.plusSeconds(60));
            }
            if (current == null) {
                current = queryRun.acceptQuota(
                        com.aiplatform.foundation.trustedquery.domain.query.model
                                .QueryQuotaSnapshot.forAcceptedQuery(
                                        queryRun.createdAt(),
                                        quota.dailyQueryLimit(),
                                        1,
                                        quota.concurrentQueryLimit(),
                                        quota.runningLease()));
                key = idempotencyKey;
                fingerprint = requestFingerprint;
                return new QueryRunClaim(current, QueryRunClaimState.CLAIMED);
            }
            assertEquals(key, idempotencyKey);
            assertEquals(fingerprint, requestFingerprint);
            return new QueryRunClaim(
                    current,
                    current.status()
                                    == com.aiplatform.foundation.trustedquery.domain.query.model
                                            .QueryRunStatus.RUNNING
                            ? QueryRunClaimState.IN_PROGRESS
                            : QueryRunClaimState.REPLAYED);
        }

        @Override
        public Optional<QueryRun> find(TenantId tenantId, QueryRunId queryRunId) {
            if (current != null
                    && current.tenantId().equals(tenantId)
                    && current.id().equals(queryRunId)) {
                return Optional.of(current);
            }
            return Optional.empty();
        }

        @Override
        public List<com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEvent> findEvents(
                TenantId tenantId,
                QueryRunId queryRunId,
                long afterSequence,
                int limit) {
            return List.of();
        }
    }
}
