package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = PostgresEncryptedModelCredentialStoreTest.TestApplication.class)
@Testcontainers
class PostgresEncryptedModelCredentialStoreTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PostgresEncryptedModelCredentialStore credentials;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach
    void prepareConfiguration() {
        jdbc.update("DELETE FROM model_runtime.model_configuration_credential");
        jdbc.update("DELETE FROM model_runtime.model_configuration");
        jdbc.update("DELETE FROM model_runtime.controlled_model_catalog_entry");
        var createdAt = Timestamp.from(Instant.parse("2026-07-28T00:00:00Z"));
        jdbc.update(
                """
                INSERT INTO model_runtime.controlled_model_catalog_entry
                    (id, provider_kind, endpoint, model_code, model_type, status, version, created_at)
                VALUES (3001, 'OPENAI_COMPATIBLE', 'https://example.com/v1', 'qwen-plus', 'CHAT', 'PUBLISHED', 1, ?)
                """,
                createdAt);
        jdbc.update(
                """
                INSERT INTO model_runtime.model_configuration
                    (id, scope, tenant_id, owner_membership_id, display_name, catalog_entry_id, status, version, created_at)
                VALUES (4001, 'TENANT', 2001, NULL, '租户模型', 3001, 'DRAFT', 0, ?)
                """,
                createdAt);
    }

    @Test
    void 写入密钥只保存密文并在受控回调中短暂解密() {
        try (var secret = SecretValue.copyOf("model-api-key".toCharArray())) {
            credentials.rotate(new ModelConfigurationId(4001L), secret);
        }

        var ciphertext = jdbc.queryForObject(
                "SELECT ciphertext FROM model_runtime.model_configuration_credential WHERE model_configuration_id = 4001",
                byte[].class);
        assertFalse(Arrays.equals("model-api-key".getBytes(), ciphertext));
        assertEquals("model-api-key", credentials.useCredential(
                new ModelConfigurationId(4001L), value -> value.use(String::new)));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration")
    static class TestApplication {

        @Bean
        SecretEncryptionKeyProvider secretEncryptionKeyProvider() {
            return new SecretEncryptionKeyProvider() {
                @Override
                public ActiveKey activeKey() {
                    return new ActiveKey("V1", key());
                }

                @Override
                public SecretValue resolve(String keyVersion) {
                    return key();
                }

                private SecretValue key() {
                    return SecretValue.copyOf(
                            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=".toCharArray());
                }
            };
        }

        @Bean
        PostgresEncryptedModelCredentialStore encryptedModelCredentialStore(
                ModelConfigurationCredentialMapper mapper,
                SecretEncryptionKeyProvider keyProvider) {
            return new PostgresEncryptedModelCredentialStore(mapper, keyProvider);
        }
    }
}
