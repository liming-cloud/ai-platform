package com.aiplatform.foundation.vectorstore.pgvector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.knowledgedata.api.extension.IndexCandidateQuery;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVector;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorBatch;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@SpringBootTest(classes = PgVectorIndexStoreTest.TestApplication.class)
@Testcontainers
class PgVectorIndexStoreTest {

    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId OTHER_TENANT =
            new TenantId(100000000000000002L);
    private static final long SPACE = 210000000000000001L;
    private static final long DEPLOYMENT = 320000000000000001L;
    private static final long INDEX = 230000000000000001L;
    private static final long OTHER_INDEX = 230000000000000002L;
    private static final long SOURCE = 220000000000000001L;
    private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PgVectorIndexStore store;

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
        insertActiveIndex(TENANT, INDEX);
        insertActiveIndex(OTHER_TENANT, OTHER_INDEX);
    }

    @Test
    void 精确余弦检索只读取租户当前活动索引并稳定排序() {
        store.replace(batch(
                TENANT,
                INDEX,
                List.of(
                        vector("1".repeat(64), 0, "一层商品", new float[] {1, 0, 0, 0}),
                        vector("2".repeat(64), 1, "二层商品", new float[] {0, 1, 0, 0}))));
        store.replace(batch(
                OTHER_TENANT,
                OTHER_INDEX,
                List.of(vector(
                        "3".repeat(64),
                        0,
                        "其他租户秘密",
                        new float[] {1, 0, 0, 0}))));

        var result = store.retrieve(new IndexCandidateQuery(
                TENANT,
                SPACE,
                DEPLOYMENT,
                1,
                new float[] {1, 0, 0, 0},
                10));

        assertEquals(2, result.size());
        assertEquals("一层商品", result.getFirst().content());
        assertEquals(1.0, result.getFirst().score(), 0.000001);
        assertTrue(result.stream()
                .noneMatch(value -> value.content().contains("秘密")));
    }

    @Test
    void 嵌入修订或维度不匹配时不返回候选() {
        store.replace(batch(
                TENANT,
                INDEX,
                List.of(vector(
                        "1".repeat(64),
                        0,
                        "一层商品",
                        new float[] {1, 0, 0, 0}))));

        assertTrue(store.retrieve(new IndexCandidateQuery(
                TENANT,
                SPACE,
                DEPLOYMENT,
                2,
                new float[] {1, 0, 0, 0},
                10)).isEmpty());
        assertTrue(store.retrieve(new IndexCandidateQuery(
                TENANT,
                SPACE,
                DEPLOYMENT,
                1,
                new float[] {1, 0, 0},
                10)).isEmpty());
    }

    @Test
    void 冻结索引被新版本替代后仍按指定版本检索() {
        store.replace(batch(
                TENANT,
                INDEX,
                List.of(vector(
                        "1".repeat(64),
                        0,
                        "冻结版本证据",
                        new float[] {1, 0, 0, 0}))));
        jdbc.update(
                """
                UPDATE knowledge_data.index_version
                SET status = 'SUPERSEDED',
                    superseded_at = ?
                WHERE tenant_id = ?
                  AND id = ?
                """,
                NOW.plusSeconds(60).atOffset(ZoneOffset.UTC),
                TENANT.value(),
                INDEX);

        var result = store.retrieve(new IndexCandidateQuery(
                TENANT,
                SPACE,
                INDEX,
                DEPLOYMENT,
                1,
                new float[] {1, 0, 0, 0},
                10));

        assertEquals(1, result.size());
        assertEquals("冻结版本证据", result.getFirst().content());
    }

    private static IndexVectorBatch batch(
            TenantId tenantId,
            long indexVersionId,
            List<IndexVector> vectors) {
        return new IndexVectorBatch(
                tenantId,
                SPACE,
                indexVersionId,
                DEPLOYMENT,
                1,
                4,
                vectors);
    }

    private static IndexVector vector(
            String chunkId,
            int ordinal,
            String content,
            float[] embedding) {
        return new IndexVector(
                SOURCE,
                1,
                chunkId,
                ordinal,
                content,
                "a".repeat(64),
                embedding);
    }

    private void insertActiveIndex(TenantId tenantId, long indexVersionId) {
        var time = NOW.atOffset(ZoneOffset.UTC);
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, status, version, created_by, created_at
                ) VALUES (?, ?, ?, 'DRAFT', 0, ?, ?)
                """,
                tenantId.value(),
                SPACE,
                "空间-" + tenantId,
                730000000000000010L,
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
                    ?, ?, ?, ?, 'paragraph-window-v1', ?, 1, 4,
                    'ACTIVE', 2, ?, 1, NULL, ?, ?, ?, ?, NULL
                )
                """,
                tenantId.value(),
                SPACE,
                indexVersionId,
                "b".repeat(64),
                DEPLOYMENT,
                "c".repeat(64),
                730000000000000010L,
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
    }
}
