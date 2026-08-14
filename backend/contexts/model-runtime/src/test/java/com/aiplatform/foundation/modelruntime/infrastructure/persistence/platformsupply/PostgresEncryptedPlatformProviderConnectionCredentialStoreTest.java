package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
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

/** 验证平台范围厂商连接凭据与加密器的持久化算法契约。 */
@SpringBootTest(classes = PostgresEncryptedPlatformProviderConnectionCredentialStoreTest.TestApplication.class)
@Testcontainers
class PostgresEncryptedPlatformProviderConnectionCredentialStoreTest {

    private static final long CONNECTION_ID = 320000000000000001L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PostgresEncryptedPlatformProviderConnectionCredentialStore credentials;

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
    void prepareConnection() {
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_credential");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection");
        jdbc.update(
                """
                INSERT INTO model_runtime.platform_provider_connection
                    (id, scope, provider_kind, display_name, endpoint, region, authentication_type,
                     credential_reference, status, version, last_probe_succeeded, last_probe_code, last_probed_at, created_at)
                VALUES (?, 'PLATFORM', 'DETERMINISTIC', '测试平台连接', 'memory://deterministic', '本地', 'API_KEY',
                        'secret://platform-provider-connection/320000000000000001', 'DRAFT', 0, NULL, NULL, NULL, ?)
                """,
                CONNECTION_ID,
                Timestamp.from(Instant.parse("2026-08-14T01:00:00Z")));
    }

    @Test
    void 写入平台连接APIKey必须接受当前AESGCM算法且只保存密文() {
        var reference = new CredentialReference("secret://platform-provider-connection/320000000000000001");
        try (var secret = SecretValue.copyOf("platform-provider-api-key".toCharArray())) {
            credentials.write(reference, secret);
        }

        assertEquals("AES-256-GCM", jdbc.queryForObject(
                "SELECT algorithm FROM model_runtime.platform_provider_connection_credential WHERE platform_provider_connection_id = ?",
                String.class,
                CONNECTION_ID));
        var ciphertext = jdbc.queryForObject(
                "SELECT ciphertext FROM model_runtime.platform_provider_connection_credential WHERE platform_provider_connection_id = ?",
                byte[].class,
                CONNECTION_ID);
        assertFalse(Arrays.equals("platform-provider-api-key".getBytes(), ciphertext));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
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
        PostgresEncryptedPlatformProviderConnectionCredentialStore credentials(
                JdbcTemplate jdbc, SecretEncryptionKeyProvider keyProvider) {
            return new PostgresEncryptedPlatformProviderConnectionCredentialStore(jdbc, keyProvider);
        }
    }
}
