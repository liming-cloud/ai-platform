package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.testrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.AssistantTestQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.AssistantTestQuotaLimitType;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestCitation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRequesterId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRun;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestTarget;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestUsage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.repository.AssistantTestRunRepository;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestCitationMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantTestRunMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.repository.PostgresAssistantTestRunRepository;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
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

@SpringBootTest(classes = PostgresAssistantTestRunRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresAssistantTestRunRepositoryTest {

    private static final TenantId STORE = new TenantId(100000000000000001L);
    private static final TenantId CAMPUS = new TenantId(100000000000000002L);
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private AssistantTestRunRepository repository;

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
                        "classpath:db/migration/knowledge-data",
                        "classpath:db/migration/model-runtime",
                        "classpath:db/migration/assistant-delivery"));
    }

    @BeforeEach
    void clearRuns() {
        jdbc.update("DELETE FROM assistant_delivery.assistant_test_run_citation");
        jdbc.update("DELETE FROM assistant_delivery.assistant_test_run");
    }

    @Test
    void 应原子占用独立测试配额并持久化不含正文的终态事实() {
        var accepted = repository.claim(running(STORE, 1L, NOW),
                new AssistantTestQuotaPolicy(2, 2, Duration.ofMinutes(5)));

        assertEquals(AssistantTestRunStatus.RUNNING, accepted.status());
        assertEquals(1, accepted.quota().dailyOrdinal());
        assertTrue(repository.find(STORE, accepted.id()).isPresent());
        assertTrue(repository.find(CAMPUS, accepted.id()).isEmpty());

        var answered = accepted.answer(
                new AssistantTestUsage(10, 20),
                java.util.List.of(new AssistantTestCitation(
                        1, 200000000000000001L, 3, "chunk-001", 0)),
                NOW.plusSeconds(3));
        repository.complete(answered, accepted.version());

        var restored = repository.find(STORE, accepted.id()).orElseThrow();
        assertEquals(AssistantTestRunStatus.ANSWERED, restored.status());
        assertEquals(30, restored.usage().totalTokens());
        assertEquals("chunk-001", restored.citations().getFirst().chunkId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM assistant_delivery.assistant_test_run_citation", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'assistant_delivery'
                  AND table_name = 'assistant_test_run'
                  AND column_name IN ('question', 'answer')
                """,
                Integer.class));
    }

    @Test
    void 并发和每日测试配额必须独立失败关闭() {
        repository.claim(running(STORE, 1L, NOW),
                new AssistantTestQuotaPolicy(1, 1, Duration.ofMinutes(5)));

        var concurrent = assertThrows(
                AssistantTestQuotaExceededException.class,
                () -> repository.claim(running(STORE, 2L, NOW.plusSeconds(1)),
                        new AssistantTestQuotaPolicy(2, 1, Duration.ofMinutes(5))));
        assertEquals(AssistantTestQuotaLimitType.CONCURRENT, concurrent.limitType());

        repository.complete(
                repository.find(STORE, new AssistantTestRunId(1L)).orElseThrow()
                        .fail(NOW.plusSeconds(2)),
                0);
        var daily = assertThrows(
                AssistantTestQuotaExceededException.class,
                () -> repository.claim(running(STORE, 3L, NOW.plusSeconds(3)),
                        new AssistantTestQuotaPolicy(1, 2, Duration.ofMinutes(5))));
        assertEquals(AssistantTestQuotaLimitType.DAILY, daily.limitType());
    }

    private static AssistantTestRun running(TenantId tenantId, long id, Instant time) {
        return AssistantTestRun.start(
                tenantId,
                new AssistantTestRunId(id),
                new AssistantTestRequesterId(100000000000000002L),
                new AssistantTestTarget(
                        300000000000000001L,
                        200000000000000001L,
                        230000000000000001L,
                        5,
                        new GovernedChatModelBinding(500000000000000001L, 7, 6, 2, 1024),
                        new GovernedEmbeddingModelBinding(500000000000000002L, 3, 8, 4, 1536),
                        "只依据编号证据回答，并在结论后标注引用。",
                        512,
                        5,
                        0.2,
                        CitationPolicy.REQUIRED,
                        RefusalPolicy.INSUFFICIENT_EVIDENCE),
                "a".repeat(64),
                time);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper")
    static class TestApplication {

        @Bean
        AssistantTestRunRepository assistantTestRunRepository(
                AssistantTestRunMapper mapper,
                AssistantTestCitationMapper citationMapper,
                EntityIdGenerator identifiers,
                PlatformTransactionManager transactions) {
            return new PostgresAssistantTestRunRepository(mapper, citationMapper, identifiers, transactions);
        }
    }
}
