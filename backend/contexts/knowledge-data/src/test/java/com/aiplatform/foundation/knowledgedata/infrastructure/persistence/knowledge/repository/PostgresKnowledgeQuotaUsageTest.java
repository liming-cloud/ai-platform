package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.UUID;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeQuotaUsageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 真实数据库证明旧来源的已用量聚合与未知来源失败关闭。 */
@SpringBootTest(classes = PostgresKnowledgeQuotaUsageTest.TestApplication.class)
@Testcontainers
class PostgresKnowledgeQuotaUsageTest {

    private static final long TENANT = 100000000000000071L;
    private static final long OTHER = 100000000000000072L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword(UUID.randomUUID().toString());

    @Autowired private PostgresKnowledgeQuotaUsage usage;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/knowledge-data");
    }

    @BeforeEach
    void facts() {
        jdbc.update("DELETE FROM knowledge_data.governed_data_source");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_content");
        jdbc.update("DELETE FROM knowledge_data.text_data_source");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        space(TENANT, 210000000000000071L, "DRAFT");
        space(TENANT, 210000000000000072L, "DRAFT");
        space(TENANT, 210000000000000073L, "DELETED");
        space(OTHER, 210000000000000074L, "DRAFT");
        source(TENANT, 210000000000000071L, 310000000000000071L, "first");
        source(TENANT, 210000000000000071L, 310000000000000072L, "second");
        source(TENANT, 210000000000000072L, 310000000000000073L, "third");
        source(OTHER, 210000000000000074L, 310000000000000074L, "other");
        source(TENANT, 210000000000000073L, 310000000000000075L, "deleted-space");
        content(TENANT, 210000000000000071L, 310000000000000071L, 1, 100L);
        content(TENANT, 210000000000000071L, 310000000000000071L, 2, 200L);
        content(TENANT, 210000000000000071L, 310000000000000072L, 1, 30L);
        content(TENANT, 210000000000000072L, 310000000000000073L, 1, 400L);
        content(OTHER, 210000000000000074L, 310000000000000074L, 1, 900L);
        content(TENANT, 210000000000000073L, 310000000000000075L, 1, 9_000L);
    }

    @Test
    void 使用当前租户的最大每库用量且保留旧版本内容字节() {
        var snapshot = usage.snapshot(TENANT);
        assertEquals(2L, snapshot.knowledgeBases());
        assertEquals(2L, snapshot.documentsPerKnowledgeBase());
        assertEquals(BigInteger.valueOf(400L), snapshot.bytesPerKnowledgeBase());
        assertTrue(snapshot.complete());
    }

    @Test
    void 受治理来源尚无统一文档口径时不得报告用量完整() {
        jdbc.update("""
                INSERT INTO knowledge_data.governed_data_source
                    (tenant_id, knowledge_space_id, id, source_type, name, status, version, created_by, created_at)
                VALUES (?, ?, ?, 'FILE', '受治理来源', 'DRAFT', 0, 1, now())
                """, TENANT, 210000000000000071L, 410000000000000071L);

        assertFalse(usage.snapshot(TENANT).complete());
        assertTrue(usage.snapshot(OTHER).complete());
    }

    private void space(long tenant, long id, String status) {
        jdbc.update("""
                INSERT INTO knowledge_data.knowledge_space
                    (tenant_id, id, name, status, version, created_by, created_at)
                VALUES (?, ?, ?, ?, 0, 1, now())
                """, tenant, id, "space-" + id, status);
    }

    private void source(long tenant, long space, long id, String name) {
        jdbc.update("""
                INSERT INTO knowledge_data.text_data_source
                    (tenant_id, knowledge_space_id, id, name, status, version, content_version,
                     published_content_version, created_by, created_at)
                VALUES (?, ?, ?, ?, 'CONTENT_READY', 0, 2, 0, 1, now())
                """, tenant, space, id, name);
    }

    private void content(long tenant, long space, long source, int version, long bytes) {
        jdbc.update("""
                INSERT INTO knowledge_data.text_data_source_content
                    (id, tenant_id, knowledge_space_id, data_source_id, version, object_key,
                     sha256, byte_size, media_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'text/plain; charset=UTF-8', now())
                """, 510000000000000000L + (source % 1000) * 10 + version, tenant, space, source, version,
                "private/" + source + "/" + version, "a".repeat(64), bytes);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = KnowledgeQuotaUsageMapper.class)
    static class TestApplication {
        @org.springframework.context.annotation.Bean
        PostgresKnowledgeQuotaUsage usage(KnowledgeQuotaUsageMapper mapper) {
            return new PostgresKnowledgeQuotaUsage(mapper);
        }
    }
}
