package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunConcurrentModificationException;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunIdempotencyConflictException;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaLimitType;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.application.operations.facade.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.application.operations.query.QueryUsageFactQuery;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final QueryRequesterId MEMBER =
            new QueryRequesterId(3382738893456539650L);
    private static final long ASSISTANT = 410000000000000001L;
    private static final long ASSISTANT_VERSION = 420000000000000001L;
    private static final long CHAT = 320000000000000001L;
    private static final long SPACE = 210000000000000001L;
    private static final long INDEX = 230000000000000001L;
    private static final long EMBEDDING = 320000000000000002L;
    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
    private static final QueryQuotaPolicy QUOTA =
            new QueryQuotaPolicy(100, 4, Duration.ofMinutes(2));
    private static final AtomicLong IDENTIFIERS =
            new AtomicLong(430000000000000000L);

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private QueryRunRepository repository;

    @Autowired
    private QueryOperationsFactSource operations;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("foundation.persistence.snowflake.worker-id", () -> "1");
        registry.add("foundation.persistence.snowflake.datacenter-id", () -> "1");
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
                "b".repeat(64),
                QUOTA);
        var inProgress = repository.claim(
                running(),
                "query-key-001",
                "b".repeat(64),
                QUOTA);
        var answered = claimed.queryRun().answer(
                "牛奶位于一层。\n\n引用：[1]",
                List.of(new QueryCitation(
                        1,
                        220000000000000001L,
                        1,
                        "c".repeat(64),
                        0,
                        "牛奶位于一层冷藏区。",
                        0.9)),
                new QueryModelUsage(10, 5, 15),
                NOW.plusSeconds(1));
        repository.complete(answered, claimed.queryRun().version());
        var replayed = repository.claim(
                running(),
                "query-key-001",
                "b".repeat(64),
                QUOTA);
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
                com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEventType
                        .QUERY_ANSWERED,
                allEvents.getLast().type());
        assertEquals(answered.citations(), allEvents.getLast().citations());
        assertEquals(List.of(2L), resumedEvents.stream()
                .map(event -> event.sequence())
                .toList());
        assertTrue(repository.find(
                new TenantId(7100000000000000001L),
                answered.id()).isEmpty());
        assertTrue(repository.findEvents(
                new TenantId(7100000000000000002L),
                answered.id(),
                0,
                10).isEmpty());
    }

    @Test
    void 不同请求不能复用幂等键且终态不能重复提交() {
        var running = running();
        var claimed = repository.claim(
                running,
                "query-key-002",
                "d".repeat(64),
                QUOTA);
        var refused = claimed.queryRun().refuse(
                com.aiplatform.foundation.trustedquery.domain.query.model.QueryRefusalReason
                        .INSUFFICIENT_EVIDENCE,
                NOW.plusSeconds(1));
        repository.complete(refused, 0);

        assertThrows(
                QueryRunIdempotencyConflictException.class,
                () -> repository.claim(
                        running(),
                        "query-key-002",
                        "e".repeat(64),
                        QUOTA));
        assertThrows(
                QueryRunConcurrentModificationException.class,
                () -> repository.complete(refused, 0));
    }

    @Test
    void 每日限额在同一事务内占用且幂等重放不重复计量() {
        var policy = new QueryQuotaPolicy(1, 1, Duration.ofMinutes(2));
        var first = runningAt(NOW);
        var claimed = repository.claim(
                first,
                "daily-key-001",
                "1".repeat(64),
                policy);
        repository.complete(
                claimed.queryRun().refuse(
                        com.aiplatform.foundation.trustedquery.domain.query.model
                                .QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                        NOW.plusSeconds(1)),
                0);

        var replayed = repository.claim(
                runningAt(NOW.plusSeconds(2)),
                "daily-key-001",
                "1".repeat(64),
                policy);
        var exceeded = assertThrows(
                QueryQuotaExceededException.class,
                () -> repository.claim(
                        runningAt(NOW.plusSeconds(2)),
                        "daily-key-002",
                        "2".repeat(64),
                        policy));

        assertEquals(QueryRunClaimState.REPLAYED, replayed.state());
        assertEquals(QueryQuotaLimitType.DAILY, exceeded.limitType());
        assertEquals(1, exceeded.limit());
        assertEquals(Instant.parse("2026-07-28T00:00:00Z"), exceeded.retryAt());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM trusted_query.query_run",
                        Integer.class));
    }

    @Test
    void 运行租约限制并发且过期后自动释放() {
        var policy = new QueryQuotaPolicy(10, 1, Duration.ofSeconds(30));
        var first = repository.claim(
                runningAt(NOW),
                "concurrent-key-001",
                "3".repeat(64),
                policy);

        var exceeded = assertThrows(
                QueryQuotaExceededException.class,
                () -> repository.claim(
                        runningAt(NOW.plusSeconds(10)),
                        "concurrent-key-002",
                        "4".repeat(64),
                        policy));
        var afterExpiry = repository.claim(
                runningAt(NOW.plusSeconds(31)),
                "concurrent-key-003",
                "5".repeat(64),
                policy);

        assertEquals(QueryRunClaimState.CLAIMED, first.state());
        assertEquals(QueryQuotaLimitType.CONCURRENT, exceeded.limitType());
        assertEquals(NOW.plusSeconds(30), exceeded.retryAt());
        assertEquals(QueryRunClaimState.CLAIMED, afterExpiry.state());
        assertEquals(2, afterExpiry.queryRun().quota().dailyOrdinal());
    }

    @Test
    void 并发竞争不能共同越过每日限额() throws Exception {
        var policy = new QueryQuotaPolicy(1, 4, Duration.ofMinutes(2));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        try {
                            repository.claim(
                                    runningAt(NOW),
                                    "race-key-" + index,
                                    Integer.toHexString(index + 10).repeat(64),
                                    policy);
                            return true;
                        } catch (QueryQuotaExceededException exception) {
                            return false;
                        }
                    }))
                    .toList();

            start.countDown();

            assertEquals(
                    1,
                    futures.stream()
                            .filter(future -> {
                                try {
                                    return future.get();
                                } catch (Exception exception) {
                                    throw new AssertionError(exception);
                                }
                            })
                            .count());
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM trusted_query.query_run",
                        Integer.class));
    }

    @Test
    void 用量和审计投影只读取当前租户的脱敏查询事实() {
        var answeredClaim = repository.claim(
                runningAt(NOW),
                "operations-key-001",
                "6".repeat(64),
                QUOTA);
        repository.complete(
                answeredClaim.queryRun().answer(
                        "牛奶位于一层。\n\n引用：[1]",
                        List.of(new QueryCitation(
                                1,
                                220000000000000002L,
                                1,
                                "7".repeat(64),
                                0,
                                "牛奶位于一层冷藏区。",
                                0.9)),
                        new QueryModelUsage(10, 5, 15),
                        NOW.plusSeconds(1)),
                0);
        var refusedClaim = repository.claim(
                runningAt(NOW.plusSeconds(2)),
                "operations-key-002",
                "8".repeat(64),
                QUOTA);
        repository.complete(
                refusedClaim.queryRun().refuse(
                        com.aiplatform.foundation.trustedquery.domain.query.model
                                .QueryRefusalReason.INSUFFICIENT_EVIDENCE,
                        NOW.plusSeconds(3)),
                0);
        var failedClaim = repository.claim(
                runningAt(NOW.plusSeconds(4)),
                "operations-key-003",
                "9".repeat(64),
                QUOTA);
        repository.complete(
                failedClaim.queryRun().fail(NOW.plusSeconds(5)),
                0);

        var usage = operations.summarize(new QueryUsageFactQuery(
                TENANT,
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"),
                NOW.plusSeconds(6)));
        var firstPage = operations.listAuditRecords(TENANT, new PageQuery(1, 1));
        var secondPage = operations.listAuditRecords(
                TENANT,
                new PageQuery(2, 1));
        var lastPage = operations.listAuditRecords(
                TENANT,
                new PageQuery(3, 1));
        var overflowPage = operations.listAuditRecords(
                TENANT,
                new PageQuery(4, 1));
        var crossTenant = operations.listAuditRecords(
                new TenantId(7100000000000000003L),
                new PageQuery(1, 20));

        assertEquals(3, usage.acceptedQueries());
        assertEquals(1, usage.answeredQueries());
        assertEquals(1, usage.refusedQueries());
        assertEquals(1, usage.failedQueries());
        assertEquals(15, usage.totalTokens());
        assertEquals(3, usage.dailyUsed());
        assertEquals(0, usage.concurrentUsed());
        assertEquals(
                failedClaim.queryRun().id().toString(),
                firstPage.items().getFirst().queryRunId());
        assertEquals(
                refusedClaim.queryRun().id().toString(),
                secondPage.items().getFirst().queryRunId());
        assertEquals(
                answeredClaim.queryRun().id().toString(),
                lastPage.items().getFirst().queryRunId());
        assertEquals(3, firstPage.totalElements());
        assertEquals(3, firstPage.totalPages());
        assertTrue(!firstPage.hasPrevious());
        assertTrue(firstPage.hasNext());
        assertTrue(secondPage.hasPrevious());
        assertTrue(secondPage.hasNext());
        assertTrue(lastPage.hasPrevious());
        assertTrue(!lastPage.hasNext());
        assertTrue(overflowPage.items().isEmpty());
        assertEquals(3, overflowPage.totalElements());
        assertTrue(!overflowPage.hasNext());
        assertTrue(crossTenant.items().isEmpty());
    }

    private static QueryRun running() {
        return runningAt(NOW);
    }

    private static QueryRun runningAt(Instant time) {
        return QueryRun.start(
                TENANT,
                new QueryRunId(IDENTIFIERS.incrementAndGet()),
                ASSISTANT_VERSION,
                MEMBER,
                "a".repeat(64),
                time);
    }

    private void seedAssistantVersion() {
        var provider = 310000000000000001L;
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

        @Bean
        QueryOperationsFactSource queryOperationsFactSource(
                QueryRunMapper mapper) {
            return new PostgresQueryOperationsFactSource(mapper);
        }
    }
}
