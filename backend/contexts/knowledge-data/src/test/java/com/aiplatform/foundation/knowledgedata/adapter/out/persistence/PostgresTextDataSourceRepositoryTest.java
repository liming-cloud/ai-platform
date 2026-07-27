package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentAttachment;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourcePublication;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.model.TextDataSource;
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

@SpringBootTest(classes = PostgresTextDataSourceRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresTextDataSourceRepositoryTest {

    private static final TenantId STORE_TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT =
            new TenantId(100000000000000002L);
    private static final KnowledgeSpaceId STORE_SPACE =
            new KnowledgeSpaceId(210000000000000001L);
    private static final AtomicLong SOURCE_IDS =
            new AtomicLong(220000000000000000L);
    private static final AtomicLong TECHNICAL_IDS =
            new AtomicLong(540000000000000000L);
    private static final MembershipId CREATOR =
            new MembershipId(4921825928971026434L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T01:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TextDataSourceRepository repository;

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
    void prepareFacts() {
        jdbc.update("DELETE FROM knowledge_data.outbox_event");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_request");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_content");
        jdbc.update("DELETE FROM knowledge_data.text_data_source");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        insertSpace(STORE_TENANT, STORE_SPACE);
        insertSpace(CAMPUS_TENANT, STORE_SPACE);
    }

    @Test
    void 创建数据源支持历史结果重放并隔离租户() {
        var sourceId = nextSourceId();
        var first = repository.create(creation(
                STORE_TENANT,
                STORE_SPACE,
                sourceId,
                "门店商品文本",
                "create-source-0001",
                "a".repeat(64)), 20);
        var replay = repository.create(creation(
                STORE_TENANT,
                STORE_SPACE,
                nextSourceId(),
                "门店商品文本",
                "create-source-0001",
                "a".repeat(64)), 20);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(sourceId, replay.source().id());
        assertEquals(DataSourceStatus.DRAFT, replay.source().status());
        assertEquals(1, countSources(STORE_TENANT));
        assertEquals(1, countEvents(STORE_TENANT, "TEXT_DATA_SOURCE_CREATED"));

        repository.create(creation(
                CAMPUS_TENANT,
                STORE_SPACE,
                nextSourceId(),
                "门店商品文本",
                "create-source-0001",
                "a".repeat(64)), 20);
        assertEquals(1, countSources(CAMPUS_TENANT));

        assertThrows(IdempotencyConflictException.class, () -> repository.create(creation(
                STORE_TENANT,
                STORE_SPACE,
                nextSourceId(),
                "其他文本",
                "create-source-0001",
                "b".repeat(64)), 20));
    }

    @Test
    void 并发创建不能突破租户数据源配额() throws Exception {
        var tasks = new ArrayList<Callable<Boolean>>();
        for (var index = 0; index < 3; index++) {
            var sequence = index;
            tasks.add(() -> {
                try {
                    repository.create(creation(
                            STORE_TENANT,
                            STORE_SPACE,
                            nextSourceId(),
                            "配额文本" + sequence,
                            "quota-source-000" + sequence,
                            Integer.toHexString(sequence).repeat(64)), 2);
                    return true;
                } catch (DataSourceQuotaExceededException exception) {
                    return false;
                }
            });
        }

        try (var executor = Executors.newFixedThreadPool(3)) {
            var results = executor.invokeAll(tasks);
            assertEquals(2, results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count());
        }
        assertEquals(2, countSources(STORE_TENANT));
        assertEquals(2, countEvents(STORE_TENANT, "TEXT_DATA_SOURCE_CREATED"));
    }

    @Test
    void 内容关联和发布保留不可变版本及可重放结果() {
        var source = repository.create(creation(
                STORE_TENANT,
                STORE_SPACE,
                nextSourceId(),
                "校园信息文本",
                "create-source-0002",
                "a".repeat(64)), 20).source();
        var content = content(1, "b".repeat(64), "tenant/object-v1.txt");

        var attached = repository.attach(new TextContentAttachment(
                STORE_TENANT,
                STORE_SPACE,
                source.id(),
                0,
                content,
                "attach-content-001",
                "c".repeat(64)));
        var published = repository.publish(new TextDataSourcePublication(
                STORE_TENANT,
                STORE_SPACE,
                source.id(),
                1,
                "publish-source-001",
                "d".repeat(64),
                CREATED_AT.plusSeconds(2)));
        var attachReplay = repository.attach(new TextContentAttachment(
                STORE_TENANT,
                STORE_SPACE,
                source.id(),
                0,
                content,
                "attach-content-001",
                "c".repeat(64)));

        assertEquals(DataSourceStatus.CONTENT_READY, attached.source().status());
        assertEquals(DataSourceStatus.PUBLISHED, published.source().status());
        assertEquals(1, published.source().publishedContentVersion());
        assertTrue(attachReplay.replayed());
        assertEquals(DataSourceStatus.CONTENT_READY, attachReplay.source().status());
        assertEquals(1, attachReplay.source().version());
        assertEquals(1, countContentVersions(source.id()));
        assertEquals(1, countEvents(STORE_TENANT, "TEXT_DATA_SOURCE_CONTENT_ATTACHED"));
        assertEquals(1, countEvents(STORE_TENANT, "TEXT_DATA_SOURCE_PUBLISHED"));
    }

    @Test
    void 并发关联同一预期版本只有一个成功且失败方不留成功事实() throws Exception {
        var source = repository.create(creation(
                STORE_TENANT,
                STORE_SPACE,
                nextSourceId(),
                "并发文本",
                "create-source-0003",
                "a".repeat(64)), 20).source();
        var tasks = new ArrayList<Callable<Boolean>>();
        for (var index = 0; index < 2; index++) {
            var sequence = index;
            tasks.add(() -> {
                try {
                    repository.attach(new TextContentAttachment(
                            STORE_TENANT,
                            STORE_SPACE,
                            source.id(),
                            0,
                            content(
                                    1,
                                    Integer.toHexString(sequence + 10).repeat(64),
                                    "tenant/concurrent-" + sequence + ".txt"),
                            "attach-concurrent-0" + sequence,
                            Integer.toHexString(sequence + 12).repeat(64)));
                    return true;
                } catch (DataSourceConcurrentModificationException exception) {
                    return false;
                }
            });
        }

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(tasks);
            assertEquals(1, results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count());
        }

        assertEquals(1, countContentVersions(source.id()));
        assertEquals(1, countRequests(STORE_TENANT, "ATTACH_CONTENT"));
        assertEquals(1, countEvents(STORE_TENANT, "TEXT_DATA_SOURCE_CONTENT_ATTACHED"));
    }

    @Test
    void 跨租户关联和无内容发布均失败且不留下成功事实() {
        var source = repository.create(creation(
                STORE_TENANT,
                STORE_SPACE,
                nextSourceId(),
                "隔离文本",
                "create-source-0004",
                "a".repeat(64)), 20).source();

        assertThrows(DataSourceNotFoundException.class, () -> repository.attach(
                new TextContentAttachment(
                        CAMPUS_TENANT,
                        STORE_SPACE,
                        source.id(),
                        0,
                        content(1, "b".repeat(64), "tenant/isolation.txt"),
                        "attach-isolation-1",
                        "c".repeat(64))));
        assertThrows(IllegalStateException.class, () -> repository.publish(
                new TextDataSourcePublication(
                        STORE_TENANT,
                        STORE_SPACE,
                        source.id(),
                        0,
                        "publish-empty-001",
                        "d".repeat(64),
                        CREATED_AT.plusSeconds(2))));

        assertEquals(0, countContentVersions(source.id()));
        assertEquals(0, countRequests(STORE_TENANT, "PUBLISH"));
        assertEquals(0, countEvents(STORE_TENANT, "TEXT_DATA_SOURCE_PUBLISHED"));
    }

    @Test
    void 数据源列表使用稳定游标并拒绝畸形游标() {
        for (var index = 0; index < 3; index++) {
            repository.create(creation(
                    STORE_TENANT,
                    STORE_SPACE,
                    nextSourceId(),
                    "分页文本" + index,
                    "page-source-000" + index,
                    Integer.toHexString(index).repeat(64)), 20);
        }

        var first = repository.findPage(STORE_TENANT, STORE_SPACE, null, 2);
        var second = repository.findPage(
                STORE_TENANT,
                STORE_SPACE,
                first.nextCursor(),
                2);

        assertEquals(2, first.items().size());
        assertEquals(1, second.items().size());
        assertTrue(first.nextCursor() != null);
        assertTrue(second.nextCursor() == null);
        assertFalse(first.items().stream()
                .map(item -> item.id())
                .toList()
                .contains(second.items().getFirst().id()));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findPage(
                        STORE_TENANT,
                        STORE_SPACE,
                        "不是有效游标",
                        2));
    }

    private static TextDataSourceCreation creation(
            TenantId tenantId,
            KnowledgeSpaceId spaceId,
            DataSourceId sourceId,
            String name,
            String idempotencyKey,
            String fingerprint) {
        return new TextDataSourceCreation(
                TextDataSource.create(
                        tenantId,
                        spaceId,
                        sourceId,
                        name,
                        CREATOR,
                        CREATED_AT),
                idempotencyKey,
                fingerprint);
    }

    private static DataSourceId nextSourceId() {
        return new DataSourceId(SOURCE_IDS.incrementAndGet());
    }

    private static TextContentReference content(
            int version,
            String sha256,
            String objectKey) {
        return new TextContentReference(
                version,
                objectKey,
                sha256,
                128,
                TextContentReference.UTF8_TEXT,
                CREATED_AT.plusSeconds(version));
    }

    private void insertSpace(TenantId tenantId, KnowledgeSpaceId spaceId) {
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, status, version, created_by, created_at
                ) VALUES (?, ?, ?, 'DRAFT', 0, ?, ?)
                """,
                tenantId.value(),
                spaceId.value(),
                "测试空间-" + tenantId.value(),
                CREATOR.value(),
                CREATED_AT.atOffset(java.time.ZoneOffset.UTC));
    }

    private int countSources(TenantId tenantId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_data.text_data_source WHERE tenant_id = ?",
                Integer.class,
                tenantId.value());
    }

    private int countContentVersions(DataSourceId sourceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_data.text_data_source_content "
                        + "WHERE data_source_id = ?",
                Integer.class,
                sourceId.value());
    }

    private int countRequests(TenantId tenantId, String operation) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_data.text_data_source_request "
                        + "WHERE tenant_id = ? AND operation = ?",
                Integer.class,
                tenantId.value(),
                operation);
    }

    private int countEvents(TenantId tenantId, String eventType) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM knowledge_data.outbox_event "
                        + "WHERE tenant_id = ? AND event_type = ?",
                Integer.class,
                tenantId.value(),
                eventType);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.knowledgedata.adapter.out.persistence")
    static class TestApplication {

        @Bean
        TextDataSourceRepository textDataSourceRepository(
                TextDataSourceMapper mapper,
                PlatformTransactionManager transactions,
                EntityIdGenerator identifiers) {
            return new PostgresTextDataSourceRepository(
                    mapper,
                    transactions,
                    identifiers);
        }

        @Bean
        EntityIdGenerator entityIdGenerator() {
            return TECHNICAL_IDS::incrementAndGet;
        }
    }
}
