package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
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

/**
 * 验证模型配置与凭据迁移的真实 PostgreSQL 约束，不加载业务服务。
 */
@SpringBootTest(classes = ModelConfigurationSchemaMigrationTest.TestApplication.class)
@Testcontainers
class ModelConfigurationSchemaMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/model-runtime");
    }

    @Test
    void 迁移必须以范围约束配置所有权且凭据表不含明文字段() {
        jdbc.update(
                """
                INSERT INTO model_runtime.controlled_model_catalog_entry
                    (id, provider_kind, endpoint, model_code, model_type, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3001L,
                "OPENAI_COMPATIBLE",
                "https://example.com/compatible-mode/v1",
                "test-chat-model",
                "CHAT",
                "PUBLISHED",
                1L,
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));

        assertThrows(Exception.class, () -> jdbc.update(
                """
                INSERT INTO model_runtime.controlled_model_catalog_entry
                    (id, provider_kind, endpoint, model_code, model_type, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3002L,
                "OPENAI_COMPATIBLE",
                "https://example.com/compatible-mode/v1?credential=forbidden",
                "test-chat-model",
                "CHAT",
                "PUBLISHED",
                1L,
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z"))));

        assertThrows(Exception.class, () -> jdbc.update(
                """
                INSERT INTO model_runtime.model_configuration
                    (id, scope, tenant_id, owner_membership_id, display_name, catalog_entry_id, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                4001L,
                "PERSONAL",
                2001L,
                null,
                "个人模型",
                3001L,
                "DRAFT",
                0L,
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z"))));

        jdbc.update(
                """
                INSERT INTO model_runtime.model_configuration
                    (id, scope, tenant_id, owner_membership_id, display_name, catalog_entry_id, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                4002L,
                "PERSONAL",
                2001L,
                5001L,
                "个人模型",
                3001L,
                "DRAFT",
                0L,
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_configuration_credential
                    (model_configuration_id, algorithm, master_key_version, data_key_nonce,
                     encrypted_data_key, ciphertext_nonce, ciphertext, fingerprint, rotated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                4002L,
                "AES-256-GCM",
                "V1",
                new byte[12],
                new byte[] {1},
                new byte[12],
                new byte[] {2},
                "a".repeat(64),
                Timestamp.from(Instant.parse("2026-07-28T00:00:00Z")));

        assertEquals(0, jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'model_runtime'
                  AND table_name = 'model_configuration_credential'
                  AND column_name = 'api_key'
                """,
                Integer.class));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
