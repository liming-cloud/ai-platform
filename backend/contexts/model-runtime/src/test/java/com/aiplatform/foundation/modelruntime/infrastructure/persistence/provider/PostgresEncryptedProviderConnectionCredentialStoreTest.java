package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.application.provider.command.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionCredentialMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository.PostgresProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretReference;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = PostgresEncryptedProviderConnectionCredentialStoreTest.TestApplication.class)
@Testcontainers
class PostgresEncryptedProviderConnectionCredentialStoreTest {

    private static final long CONNECTION_ID = 310000000000000001L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PostgresEncryptedProviderConnectionCredentialStore credentials;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProviderConnectionMapper connections;

    @Autowired
    private ProviderConnectionRequestMapper requests;

    @Autowired
    private PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach
    void prepareConnection() {
        jdbc.update("DELETE FROM model_runtime.provider_connection_credential");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        jdbc.update(
                """
                INSERT INTO model_runtime.provider_connection
                    (id, tenant_id, provider_kind, display_name, endpoint, region, authentication_type,
                     credential_reference, status, version, last_probe_succeeded, last_probe_code, last_probed_at, created_at)
                VALUES (?, 100000000000000001, 'DETERMINISTIC', '测试连接', 'memory://deterministic', '本地', 'API_KEY',
                        'secret://provider-connection/310000000000000001', 'DRAFT', 0, NULL, NULL, NULL, ?)
                """,
                CONNECTION_ID,
                Timestamp.from(Instant.parse("2026-08-10T05:00:00Z")));
    }

    @Test
    void 写入连接APIKey只保存密文且由私有引用短暂解析() {
        var reference = new CredentialReference("secret://provider-connection/310000000000000001");
        try (var secret = SecretValue.copyOf("provider-api-key".toCharArray())) {
            credentials.write(
                    new com.aiplatform.foundation.shared.kernel.tenant.TenantId(100000000000000001L),
                    reference,
                    secret);
        }

        var ciphertext = jdbc.queryForObject(
                "SELECT ciphertext FROM model_runtime.provider_connection_credential WHERE provider_connection_id = ?",
                byte[].class,
                CONNECTION_ID);
        assertFalse(Arrays.equals("provider-api-key".getBytes(), ciphertext));
        try (var resolved = credentials.resolve(new SecretReference(reference.value()))) {
            assertEquals("provider-api-key", resolved.use(String::new));
        }
    }

    @Test
    void 凭据写入失败时创建连接与幂等记录必须由同一事务回滚() {
        var connectionId = 310000000000000002L;
        var service = new ProviderConnectionService(
                new PostgresProviderConnectionRepository(connections, requests, transactions),
                mock(ProbeProviderService.class),
                (tenantId, reference, apiKey) -> {
                    throw new IllegalStateException("模拟凭据写入失败");
                },
                new SpringProviderConnectionCredentialTransaction(transactions),
                Clock.fixed(Instant.parse("2026-08-10T05:10:00Z"), ZoneOffset.UTC),
                () -> connectionId,
                10);

        try (var apiKey = SecretValue.copyOf("provider-api-key".toCharArray())) {
            assertThrows(IllegalStateException.class, () -> service.create(
                    new CreateProviderConnectionCommand(
                            writableContext(),
                            "provider-request-0002",
                            ProviderKind.DETERMINISTIC,
                            "失败回滚连接",
                            URI.create("memory://deterministic"),
                            "本地",
                            AuthenticationType.API_KEY,
                            apiKey)));
        }

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.provider_connection WHERE id = ?",
                Integer.class,
                connectionId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.provider_connection_request WHERE provider_connection_id = ?",
                Integer.class,
                connectionId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.provider_connection_credential WHERE provider_connection_id = ?",
                Integer.class,
                connectionId));
    }

    private static TrustedRequestContext writableContext() {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("model:write")),
                "trc-provider-credential");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider")
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
        PostgresEncryptedProviderConnectionCredentialStore encryptedProviderCredentialStore(
                ProviderConnectionCredentialMapper mapper,
                SecretEncryptionKeyProvider keyProvider) {
            return new PostgresEncryptedProviderConnectionCredentialStore(mapper, keyProvider);
        }
    }
}
