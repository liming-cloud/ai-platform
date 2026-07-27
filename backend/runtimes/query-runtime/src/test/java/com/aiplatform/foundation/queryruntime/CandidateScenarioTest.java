package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexLookup;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexVector;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.application.indexversion.dto.ActiveIndexSnapshot;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModel;
import com.aiplatform.foundation.trustedquery.api.CandidateSearchUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchQuery;
import com.aiplatform.foundation.trustedquery.application.candidate.DefaultCandidateSearchService;
import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = CandidateScenarioTest.TestApplication.class)
@Testcontainers
class CandidateScenarioTest {

    private static final TenantId STORE_TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT =
            new TenantId(100000000000000002L);
    private static final long SPACE = 210000000000000001L;
    private static final long STORE_INDEX = 230000000000000001L;
    private static final long CAMPUS_INDEX = 230000000000000002L;
    private static final long SOURCE = 220000000000000001L;
    private static final EmbeddingDeploymentRef DEPLOYMENT =
            new EmbeddingDeploymentRef(
                    DeterministicEmbeddingModel.DEPLOYMENT_ID,
                    DeterministicEmbeddingModel.DEPLOYMENT_REVISION,
                    DeterministicEmbeddingModel.DIMENSION);
    private static final Instant NOW = Instant.parse("2026-07-27T04:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PgVectorIndexStore vectors;

    @Autowired
    private CandidateSearchUseCase searches;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/knowledge-data");
    }

    @BeforeEach
    void prepareScenarios() {
        jdbc.update("DELETE FROM knowledge_data.index_chunk");
        jdbc.update("DELETE FROM knowledge_data.index_version_request");
        jdbc.update("DELETE FROM knowledge_data.index_build_job");
        jdbc.update("DELETE FROM knowledge_data.index_version_source");
        jdbc.update("DELETE FROM knowledge_data.index_version");
        jdbc.update("DELETE FROM knowledge_data.outbox_event");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_request");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_content");
        jdbc.update("DELETE FROM knowledge_data.text_data_source");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");

        insertActiveIndex(STORE_TENANT, STORE_INDEX);
        insertActiveIndex(CAMPUS_TENANT, CAMPUS_INDEX);
        vectors.replace(batch(
                STORE_TENANT,
                STORE_INDEX,
                "可乐位于门店一层饮料区，当前价格三元。",
                "a".repeat(64)));
        vectors.replace(batch(
                CAMPUS_TENANT,
                CAMPUS_INDEX,
                "图书馆工作日开放时间为八点到二十二点。",
                "b".repeat(64)));
    }

    @Test
    void 门店与校园固定查询命中各自候选且不交叉泄漏() {
        var store = searches.search(new CandidateSearchQuery(
                context(STORE_TENANT),
                Long.toString(SPACE),
                "门店一层饮料区的可乐价格",
                5));
        var campus = searches.search(new CandidateSearchQuery(
                context(CAMPUS_TENANT),
                Long.toString(SPACE),
                "图书馆工作日几点开放",
                5));

        assertEquals(1, store.candidates().size());
        assertTrue(store.candidates().getFirst().content().contains("可乐"));
        assertTrue(store.candidates().stream()
                .noneMatch(value -> value.content().contains("图书馆")));
        assertEquals(1, campus.candidates().size());
        assertTrue(campus.candidates().getFirst().content().contains("图书馆"));
        assertTrue(campus.candidates().stream()
                .noneMatch(value -> value.content().contains("可乐")));
    }

    private static IndexVectorBatch batch(
            TenantId tenant,
            long indexId,
            String text,
            String chunkId) {
        var model = new DeterministicEmbeddingModel();
        var embedding = model.embed(new EmbeddingRequest(
                        DEPLOYMENT,
                        List.of(text)))
                .vectors()
                .getFirst()
                .values();
        return new IndexVectorBatch(
                tenant,
                SPACE,
                indexId,
                DEPLOYMENT.deploymentId(),
                DEPLOYMENT.revision(),
                DEPLOYMENT.dimension(),
                List.of(new IndexVector(
                        SOURCE,
                        1,
                        chunkId,
                        0,
                        text,
                        "c".repeat(64),
                        embedding)));
    }

    private static TrustedRequestContext context(TenantId tenant) {
        return new TrustedRequestContext(
                tenant,
                new SubjectId(304564822427566082L),
                new MembershipId(6765477786913079298L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("knowledge:read")),
                "trace-scenario-001");
    }

    private void insertActiveIndex(TenantId tenant, long indexId) {
        var time = NOW.atOffset(ZoneOffset.UTC);
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, status, version, created_by, created_at
                ) VALUES (?, ?, ?, 'DRAFT', 0, ?, ?)
                """,
                tenant.value(),
                SPACE,
                "场景空间-" + tenant.value(),
                730000000000000011L,
                time);
        jdbc.update(
                """
                INSERT INTO knowledge_data.index_version (
                    tenant_id, knowledge_space_id, id, source_snapshot_digest,
                    chunk_policy, embedding_deployment_id,
                    embedding_deployment_revision, embedding_dimension,
                    status, version, build_digest, chunk_count, failure_code,
                    created_by, created_at, ready_at, activated_at, superseded_at
                ) VALUES (
                    ?, ?, ?, ?, 'paragraph-window-v1', ?, ?, ?,
                    'ACTIVE', 2, ?, 1, NULL, ?, ?, ?, ?, NULL
                )
                """,
                tenant.value(),
                SPACE,
                indexId,
                "d".repeat(64),
                DEPLOYMENT.deploymentId(),
                DEPLOYMENT.revision(),
                DEPLOYMENT.dimension(),
                "e".repeat(64),
                730000000000000011L,
                time,
                time,
                time);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        PgVectorIndexStore pgVectorIndexStore(JdbcTemplate jdbc) {
            return new PgVectorIndexStore(jdbc);
        }

        @Bean
        ActiveIndexLookup activeIndexLookup() {
            return (tenantId, knowledgeSpaceId) -> {
                if (SPACE != knowledgeSpaceId) {
                    return java.util.Optional.empty();
                }
                var indexId = STORE_TENANT.equals(tenantId)
                        ? STORE_INDEX
                        : CAMPUS_INDEX;
                return java.util.Optional.of(new ActiveIndexSnapshot(
                        SPACE,
                        indexId,
                        2,
                        DEPLOYMENT.deploymentId(),
                        DEPLOYMENT.revision(),
                        DEPLOYMENT.dimension()));
            };
        }

        @Bean
        ModelRuntimeResolver modelRuntimeResolver() {
            return new ModelRuntimeResolver() {

                @Override
                public ChatModelPort resolveChat(TenantId tenantId, long deploymentId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public EmbeddingModelPort resolveEmbedding(
                        TenantId tenantId,
                        long deploymentId) {
                    return new DeterministicEmbeddingModel(DEPLOYMENT);
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

        @Bean
        CandidateSearchUseCase candidateSearchUseCase(
                ActiveIndexLookup indexes,
                ModelRuntimeResolver models,
                PgVectorIndexStore vectors) {
            return new DefaultCandidateSearchService(
                    indexes,
                    models,
                    vectors);
        }
    }
}
