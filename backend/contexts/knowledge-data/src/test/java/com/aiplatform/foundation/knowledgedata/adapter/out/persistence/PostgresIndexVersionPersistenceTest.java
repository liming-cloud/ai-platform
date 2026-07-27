package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVector;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorWriter;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionStatus;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.ParagraphWindowChunker;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@SpringBootTest(classes = PostgresIndexVersionPersistenceTest.TestApplication.class)
@Testcontainers
class PostgresIndexVersionPersistenceTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId OTHER_TENANT =
            new TenantId(100000000000000002L);
    private static final KnowledgeSpaceId SPACE =
            new KnowledgeSpaceId(210000000000000001L);
    private static final MembershipId CREATOR =
            new MembershipId(3382738893456539650L);
    private static final EmbeddingConfiguration EMBEDDING =
            new EmbeddingConfiguration(
                    320000000000000001L,
                    1,
                    4);
    private static final Instant NOW = Instant.parse("2026-07-27T02:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private IndexVersionRepository repository;

    @Autowired
    private IndexBuildQueuePort queue;

    @Autowired
    private IndexBuildCompletionPort completion;

    @Autowired
    private RecordingVectorWriter vectorWriter;

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
        insertPublishedSource(TENANT, 220000000000000001L, "a".repeat(64));
        insertPublishedSource(TENANT, 220000000000000002L, "b".repeat(64));
        insertPublishedSource(
                OTHER_TENANT,
                220000000000000003L,
                "c".repeat(64));
        vectorWriter.clear();
    }

    @Test
    void 创建冻结当前已发布来源并支持租户内幂等重放() {
        var id = new IndexVersionId(230000000000000001L);
        var first = repository.create(request(
                TENANT,
                id,
                "create-index-0001",
                "d".repeat(64)));
        var replay = repository.create(request(
                TENANT,
                new IndexVersionId(230000000000000002L),
                "create-index-0001",
                "d".repeat(64)));

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(id, replay.indexVersion().id());
        assertEquals(IndexVersionStatus.BUILDING, first.indexVersion().status());
        assertEquals(2, jdbc.queryForObject(
                """
                SELECT count(*)
                FROM knowledge_data.index_version_source
                WHERE tenant_id = ? AND index_version_id = ?
                """,
                Integer.class,
                TENANT.value(),
                id.value()));
        assertTrue(first.indexVersion().sourceSnapshotDigest().matches("[0-9a-f]{64}"));
        assertTrue(repository.findPage(OTHER_TENANT, SPACE, null, 20).items().isEmpty());

        assertThrows(IdempotencyConflictException.class, () -> repository.create(request(
                TENANT,
                new IndexVersionId(230000000000000003L),
                "create-index-0001",
                "e".repeat(64))));
    }

    @Test
    void 构建租约只认领一次且产物与就绪状态同事务提交() {
        var id = new IndexVersionId(230000000000000004L);
        repository.create(request(TENANT, id, "create-index-0002", "d".repeat(64)));

        var claimed = queue.claim(NOW.plusSeconds(1), Duration.ofMinutes(1));
        var secondClaim = queue.claim(NOW.plusSeconds(2), Duration.ofMinutes(1));

        assertTrue(claimed.isPresent());
        assertTrue(secondClaim.isEmpty());
        assertEquals(2, claimed.orElseThrow().sources().size());

        var batch = batch(id);
        completion.complete(
                claimed.orElseThrow(),
                batch,
                "f".repeat(64),
                NOW.plusSeconds(3));

        assertEquals(batch, vectorWriter.lastBatch);
        var ready = repository.findPage(TENANT, SPACE, null, 20).items().getFirst();
        assertEquals(IndexVersionStatus.READY, ready.status());
        assertEquals(1, ready.chunkCount());
        assertTrue(queue.claim(NOW.plusSeconds(120), Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void 新版本激活时旧活动版本在同一事务中降级() {
        var firstId = new IndexVersionId(230000000000000005L);
        var secondId = new IndexVersionId(230000000000000006L);
        makeReady(firstId, "create-index-0003");
        var firstActive = repository.activate(
                TENANT,
                SPACE,
                firstId,
                1,
                "activate-index-001",
                "1".repeat(64),
                NOW.plusSeconds(4));
        makeReady(secondId, "create-index-0004");
        repository.activate(
                TENANT,
                SPACE,
                secondId,
                1,
                "activate-index-002",
                "2".repeat(64),
                NOW.plusSeconds(8));

        assertEquals(IndexVersionStatus.ACTIVE, firstActive.indexVersion().status());
        var states = repository.findPage(TENANT, SPACE, null, 20).items();
        assertEquals(1, states.stream()
                .filter(value -> value.status() == IndexVersionStatus.ACTIVE)
                .count());
        assertEquals(IndexVersionStatus.SUPERSEDED, states.stream()
                .filter(value -> value.id().equals(firstId))
                .findFirst()
                .orElseThrow()
                .status());
    }

    private void makeReady(IndexVersionId id, String idempotencyKey) {
        repository.create(request(TENANT, id, idempotencyKey, "d".repeat(64)));
        var lease = queue.claim(NOW.plusSeconds(1), Duration.ofMinutes(1)).orElseThrow();
        completion.complete(lease, batch(id), "f".repeat(64), NOW.plusSeconds(2));
    }

    private static IndexVersionBuildRequest request(
            TenantId tenant,
            IndexVersionId id,
            String idempotencyKey,
            String fingerprint) {
        return new IndexVersionBuildRequest(
                tenant,
                SPACE,
                id,
                EMBEDDING,
                ParagraphWindowChunker.POLICY,
                CREATOR,
                NOW,
                idempotencyKey,
                fingerprint);
    }

    private static IndexVectorBatch batch(IndexVersionId id) {
        return new IndexVectorBatch(
                TENANT,
                SPACE.value(),
                id.value(),
                EMBEDDING.deploymentId(),
                EMBEDDING.deploymentRevision(),
                EMBEDDING.dimension(),
                List.of(new IndexVector(
                        220000000000000001L,
                        1,
                        "3".repeat(64),
                        0,
                        "门店商品位于一层",
                        "4".repeat(64),
                        new float[] {1, 0, 0, 0})));
    }

    private void insertPublishedSource(
            TenantId tenant,
            long sourceId,
            String contentDigest) {
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, status, version, created_by, created_at
                ) VALUES (?, ?, ?, 'DRAFT', 0, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                tenant.value(),
                SPACE.value(),
                "测试知识空间-" + tenant.value(),
                CREATOR.value(),
                NOW.atOffset(ZoneOffset.UTC));
        jdbc.update(
                """
                INSERT INTO knowledge_data.text_data_source (
                    tenant_id, knowledge_space_id, id, name, status, version,
                    content_version, published_content_version, created_by, created_at
                ) VALUES (?, ?, ?, ?, 'PUBLISHED', 2, 1, 1, ?, ?)
                """,
                tenant.value(),
                SPACE.value(),
                sourceId,
                "来源-" + sourceId,
                CREATOR.value(),
                NOW.atOffset(ZoneOffset.UTC));
        jdbc.update(
                """
                INSERT INTO knowledge_data.text_data_source_content (
                    tenant_id, knowledge_space_id, data_source_id, version,
                    object_key, sha256, byte_size, media_type, created_at
                ) VALUES (?, ?, ?, 1, ?, ?, 16, ?, ?)
                """,
                tenant.value(),
                SPACE.value(),
                sourceId,
                "foundation-v1/" + tenant.value() + "/" + sourceId + ".txt",
                contentDigest,
                "text/plain; charset=UTF-8",
                NOW.atOffset(ZoneOffset.UTC));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.knowledgedata.adapter.out.persistence")
    static class TestApplication {

        @Bean
        RecordingVectorWriter vectorWriter() {
            return new RecordingVectorWriter();
        }

        @Bean
        IndexVersionRepository indexVersionRepository(
                IndexVersionMapper mapper,
                PlatformTransactionManager transactionManager) {
            return new PostgresIndexVersionRepository(mapper, transactionManager);
        }

        @Bean
        IndexBuildQueuePort indexBuildQueuePort(
                IndexVersionMapper mapper,
                PlatformTransactionManager transactionManager) {
            return new PostgresIndexBuildQueue(mapper, transactionManager);
        }

        @Bean
        IndexBuildCompletionPort indexBuildCompletionPort(
                IndexVersionMapper mapper,
                IndexVectorWriter writer,
                PlatformTransactionManager transactionManager) {
            return new PostgresIndexBuildCompletion(
                    mapper,
                    writer,
                    transactionManager);
        }
    }

    static final class RecordingVectorWriter implements IndexVectorWriter {

        private IndexVectorBatch lastBatch;

        @Override
        public void replace(IndexVectorBatch batch) {
            lastBatch = batch;
        }

        void clear() {
            lastBatch = null;
        }
    }
}
