package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.KnowledgeSpaceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.port.out.knowledge.KnowledgeSpaceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.knowledge.KnowledgeSpaceRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;
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

@SpringBootTest(classes = PostgresKnowledgeSpaceRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresKnowledgeSpaceRepositoryTest {

    private static final AtomicLong SPACE_IDS =
            new AtomicLong(210000000000000000L);

    private static final TenantId STORE_TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT =
            new TenantId(100000000000000002L);
    private static final MembershipId CREATOR =
            new MembershipId(4921825928971026434L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private KnowledgeSpaceRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/knowledge-data");
    }

    @BeforeEach
    void clearFacts() {
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
    }

    @Test
    void 同租户同键同请求返回原资源且同键异参失败() {
        var first = repository.create(creation(
                STORE_TENANT,
                "request-00000001",
                "a".repeat(64),
                "门店商品知识"), 3);
        var replay = repository.create(creation(
                STORE_TENANT,
                "request-00000001",
                "a".repeat(64),
                "门店商品知识"), 3);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.space().id(), replay.space().id());
        assertEquals(1, countSpaces(STORE_TENANT));

        assertThrows(IdempotencyConflictException.class, () -> repository.create(creation(
                STORE_TENANT,
                "request-00000001",
                "b".repeat(64),
                "另一知识空间"), 3));
        assertEquals(1, countSpaces(STORE_TENANT));
    }

    @Test
    void 相同名称和幂等键在两个租户中保持隔离() {
        repository.create(creation(
                STORE_TENANT,
                "request-00000002",
                "a".repeat(64),
                "通用知识"), 3);
        repository.create(creation(
                CAMPUS_TENANT,
                "request-00000002",
                "a".repeat(64),
                "通用知识"), 3);

        assertEquals(1, countSpaces(STORE_TENANT));
        assertEquals(1, countSpaces(CAMPUS_TENANT));
        assertEquals(
                1,
                repository.findPage(STORE_TENANT, null, 20).items().size());
        assertEquals(
                1,
                repository.findPage(CAMPUS_TENANT, null, 20).items().size());
    }

    @Test
    void 并发创建不能突破租户配额() throws Exception {
        var tasks = new ArrayList<Callable<Boolean>>();
        for (var index = 0; index < 4; index++) {
            var sequence = index;
            tasks.add(() -> {
                try {
                    repository.create(creation(
                            STORE_TENANT,
                            "request-quota-0" + sequence,
                            Integer.toHexString(sequence).repeat(64),
                            "知识空间" + sequence), 3);
                    return true;
                } catch (KnowledgeSpaceQuotaExceededException exception) {
                    return false;
                }
            });
        }

        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(tasks);
            assertEquals(3, results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count());
        }
        assertEquals(3, countSpaces(STORE_TENANT));
    }

    @Test
    void 游标分页不重复资源并拒绝畸形游标() {
        for (var index = 0; index < 3; index++) {
            repository.create(creation(
                    STORE_TENANT,
                    "request-page-00" + index,
                    Integer.toHexString(index).repeat(64),
                    "分页知识空间" + index), 5);
        }

        var first = repository.findPage(STORE_TENANT, null, 2);
        var second = repository.findPage(STORE_TENANT, first.nextCursor(), 2);

        assertEquals(2, first.items().size());
        assertEquals(1, second.items().size());
        assertTrue(first.nextCursor() != null);
        assertTrue(second.nextCursor() == null);
        assertFalse(first.items().stream()
                .map(KnowledgeSpace::id)
                .toList()
                .contains(second.items().getFirst().id()));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findPage(STORE_TENANT, "不是有效游标", 2));
    }

    private static KnowledgeSpaceCreation creation(
            TenantId tenantId,
            String idempotencyKey,
            String fingerprint,
            String name) {
        return new KnowledgeSpaceCreation(
                KnowledgeSpace.create(
                        tenantId,
                        new KnowledgeSpaceId(SPACE_IDS.incrementAndGet()),
                        name,
                        null,
                        CREATOR,
                        CREATED_AT),
                idempotencyKey,
                fingerprint);
    }

    private int countSpaces(TenantId tenantId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_data.knowledge_space WHERE tenant_id = ?",
                Integer.class,
                tenantId.value());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.knowledgedata.adapter.out.persistence")
    static class TestApplication {

        @Bean
        KnowledgeSpaceRepository knowledgeSpaceRepository(
                KnowledgeSpaceMapper mapper,
                PlatformTransactionManager transactions) {
            return new PostgresKnowledgeSpaceRepository(mapper, transactions);
        }
    }
}
