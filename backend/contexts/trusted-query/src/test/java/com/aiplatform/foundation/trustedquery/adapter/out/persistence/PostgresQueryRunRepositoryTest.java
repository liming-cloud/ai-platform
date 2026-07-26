package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.trustedquery.application.QueryRunConcurrentModificationException;
import com.aiplatform.foundation.trustedquery.application.QueryRunIdempotencyConflictException;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = PostgresQueryRunRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresQueryRunRepositoryTest {

    private static final TenantId TENANT = new TenantId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final MembershipId MEMBER = new MembershipId(
            UUID.fromString("30000000-0000-0000-0000-000000000001"));
    private static final UUID ASSISTANT =
            UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_VERSION =
            UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID CHAT =
            UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID SPACE =
            UUID.fromString("44000000-0000-0000-0000-000000000001");
    private static final UUID INDEX =
            UUID.fromString("45000000-0000-0000-0000-000000000001");
    private static final UUID EMBEDDING =
            UUID.fromString("46000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private QueryRunRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.flyway.locations",
                () -> String.join(
                        ",",
                        "classpath:db/migration/organization-access",
                        "classpath:db/migration/knowledge-data",
                        "classpath:db/migration/model-runtime",
                        "classpath:db/migration/assistant-delivery",
                        "classpath:db/migration/trusted-query",
                        "classpath:db/demo/organization-access"));
    }

    @BeforeEach
    void prepareFacts() {
        jdbc.update("DELETE FROM trusted_query.query_run_event");
        jdbc.update("DELETE FROM trusted_query.query_citation");
        jdbc.update("DELETE FROM trusted_query.query_run_request");
        jdbc.update("DELETE FROM trusted_query.query_run");
        jdbc.update("DELETE FROM assistant_delivery.assistant_request");
        jdbc.update("DELETE FROM assistant_delivery.assistant_version");
        jdbc.update("DELETE FROM assistant_delivery.assistant");
        jdbc.update("DELETE FROM knowledge_data.index_chunk");
        jdbc.update("DELETE FROM knowledge_data.index_version_request");
        jdbc.update("DELETE FROM knowledge_data.index_build_job");
        jdbc.update("DELETE FROM knowledge_data.index_version_source");
        jdbc.update("DELETE FROM knowledge_data.index_version");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        jdbc.update("DELETE FROM model_runtime.model_deployment_request");
        jdbc.update("DELETE FROM model_runtime.model_deployment");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        seedAssistantVersion();
    }

    @Test
    void 短事务占位终态提交和重放保证模型调用只需发生一次() {
        var running = running();

        var claimed = repository.claim(
                running,
                "query-key-001",
                "b".repeat(64));
        var inProgress = repository.claim(
                running(),
                "query-key-001",
                "b".repeat(64));
        var answered = running.answer(
                "牛奶位于一层。\n\n引用：[1]",
                List.of(new QueryCitation(
                        1,
                        UUID.randomUUID(),
                        1,
                        "c".repeat(64),
                        0,
                        "牛奶位于一层冷藏区。",
                        0.9)),
                new ModelUsage(10, 5, 15),
                NOW.plusSeconds(1));
        repository.complete(answered, running.version());
        var replayed = repository.claim(
                running(),
                "query-key-001",
                "b".repeat(64));
        var allEvents = repository.findEvents(TENANT, answered.id(), 0, 10);
        var resumedEvents = repository.findEvents(TENANT, answered.id(), 1, 10);

        assertEquals(QueryRunClaimState.CLAIMED, claimed.state());
        assertEquals(QueryRunClaimState.IN_PROGRESS, inProgress.state());
        assertEquals(QueryRunClaimState.REPLAYED, replayed.state());
        assertEquals(answered.id(), replayed.queryRun().id());
        assertEquals(1, replayed.queryRun().citations().size());
        assertEquals(15, replayed.queryRun().usage().totalTokens());
        assertEquals(List.of(1L, 2L), allEvents.stream()
                .map(event -> event.sequence())
                .toList());
        assertEquals(
                com.aiplatform.foundation.trustedquery.domain.QueryRunEventType
                        .QUERY_ANSWERED,
                allEvents.getLast().type());
        assertEquals(answered.citations(), allEvents.getLast().citations());
        assertEquals(List.of(2L), resumedEvents.stream()
                .map(event -> event.sequence())
                .toList());
        assertTrue(repository.find(
                new TenantId(UUID.randomUUID()),
                answered.id()).isEmpty());
        assertTrue(repository.findEvents(
                new TenantId(UUID.randomUUID()),
                answered.id(),
                0,
                10).isEmpty());
    }

    @Test
    void 不同请求不能复用幂等键且终态不能重复提交() {
        var running = running();
        repository.claim(running, "query-key-002", "d".repeat(64));
        var refused = running.refuse(
                com.aiplatform.foundation.trustedquery.domain.QueryRefusalReason
                        .INSUFFICIENT_EVIDENCE,
                NOW.plusSeconds(1));
        repository.complete(refused, 0);

        assertThrows(
                QueryRunIdempotencyConflictException.class,
                () -> repository.claim(
                        running(),
                        "query-key-002",
                        "e".repeat(64)));
        assertThrows(
                QueryRunConcurrentModificationException.class,
                () -> repository.complete(refused, 0));
    }

    private static QueryRun running() {
        return QueryRun.start(
                TENANT,
                UUID.randomUUID(),
                ASSISTANT_VERSION,
                MEMBER,
                "a".repeat(64),
                NOW);
    }

    private void seedAssistantVersion() {
        var provider = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO model_runtime.provider_connection (
                    tenant_id, id, provider_kind, display_name, endpoint, region,
                    authentication_type, credential_reference, status, version,
                    last_probe_succeeded, last_probe_code, last_probed_at, created_at
                ) VALUES (?, ?, 'DETERMINISTIC', '查询测试供应商',
                    'memory://deterministic', '本地', 'API_KEY',
                    'secret://test/query-provider', 'VERIFIED', 1, TRUE,
                    'DETERMINISTIC_OK', ?, ?)
                """,
                TENANT.value(),
                provider,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(120)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_deployment (
                    tenant_id, id, provider_connection_id, provider_connection_revision,
                    model_code, display_name, model_type, capabilities, context_window,
                    output_limit, embedding_dimension, status, version, created_at
                ) VALUES (?, ?, ?, 1, 'deterministic-chat', '查询测试对话部署',
                    'CHAT', ARRAY['CHAT'], 8192, 1024, NULL, 'PUBLISHED', 1, ?)
                """,
                TENANT.value(),
                CHAT,
                provider,
                Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, description, status, version, created_by, created_at
                ) VALUES (?, ?, '查询测试知识空间', NULL, 'DRAFT', 0, ?, ?)
                """,
                TENANT.value(),
                SPACE,
                MEMBER.value(),
                Timestamp.from(NOW.minusSeconds(120)));
        jdbc.update(
                """
                INSERT INTO knowledge_data.index_version (
                    tenant_id, knowledge_space_id, id, source_snapshot_digest, chunk_policy,
                    embedding_deployment_id, embedding_deployment_revision,
                    embedding_dimension, status, version, build_digest, chunk_count,
                    failure_code, created_by, created_at, ready_at, activated_at,
                    superseded_at
                ) VALUES (?, ?, ?, ?, 'fixed-800-120', ?, 1, 4, 'ACTIVE', 2, ?,
                    1, NULL, ?, ?, ?, ?, NULL)
                """,
                TENANT.value(),
                SPACE,
                INDEX,
                "f".repeat(64),
                EMBEDDING,
                "1".repeat(64),
                MEMBER.value(),
                Timestamp.from(NOW.minusSeconds(90)),
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(30)));
        jdbc.update(
                """
                INSERT INTO assistant_delivery.assistant (
                    tenant_id, id, display_name, latest_version_number,
                    version, created_by, created_at
                ) VALUES (?, ?, '查询测试助手', 1, 1, ?, ?)
                """,
                TENANT.value(),
                ASSISTANT,
                MEMBER.value(),
                Timestamp.from(NOW.minusSeconds(60)));
        jdbc.update(
                """
                INSERT INTO assistant_delivery.assistant_version (
                    tenant_id, id, assistant_id, version_number,
                    chat_deployment_id, chat_deployment_revision,
                    chat_deployment_output_limit, knowledge_space_id,
                    index_version_id, index_version_revision,
                    embedding_deployment_id, embedding_deployment_revision,
                    embedding_dimension, system_instruction, max_output_tokens,
                    retrieval_limit, minimum_score, citation_policy, refusal_policy,
                    status, version, created_by, created_at, published_at
                ) VALUES (
                    ?, ?, ?, 1, ?, 1, 1024, ?, ?, 2, ?, 1, 4,
                    '只依据授权证据回答。', 256, 5, 0.5, 'REQUIRED',
                    'INSUFFICIENT_EVIDENCE', 'PUBLISHED', 1, ?, ?, ?
                )
                """,
                TENANT.value(),
                ASSISTANT_VERSION,
                ASSISTANT,
                CHAT,
                SPACE,
                INDEX,
                EMBEDDING,
                MEMBER.value(),
                Timestamp.from(NOW.minusSeconds(30)),
                Timestamp.from(NOW.minusSeconds(20)));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = QueryRunMapper.class)
    static class TestApplication {

        @Bean
        QueryRunRepository queryRunRepository(
                QueryRunMapper mapper,
                PlatformTransactionManager transactions) {
            return new PostgresQueryRunRepository(mapper, transactions);
        }
    }
}
