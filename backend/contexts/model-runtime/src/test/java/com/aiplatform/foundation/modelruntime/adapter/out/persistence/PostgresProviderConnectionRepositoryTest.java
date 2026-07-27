package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ProviderConnectionQuotaExceededException;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
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

@SpringBootTest(classes = PostgresProviderConnectionRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresProviderConnectionRepositoryTest {

    private static final AtomicLong IDENTIFIERS =
            new AtomicLong(310000000000000100L);

    private static final TenantId STORE_TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT =
            new TenantId(100000000000000002L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T04:30:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private ProviderConnectionRepository repository;

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

    @BeforeEach
    void clearFacts() {
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
    }

    @Test
    void 同租户同键同请求返回原连接且同键异参失败() {
        var first = repository.create(
                creation(STORE_TENANT, "provider-request-0001", "a".repeat(64), "门店模型"),
                3);
        var replayed = repository.create(
                creation(STORE_TENANT, "provider-request-0001", "a".repeat(64), "门店模型"),
                3);

        assertFalse(first.replayed());
        assertTrue(replayed.replayed());
        assertEquals(first.connection().id(), replayed.connection().id());
        assertThrows(
                ProviderConnectionIdempotencyConflictException.class,
                () -> repository.create(
                        creation(
                                STORE_TENANT,
                                "provider-request-0001",
                                "b".repeat(64),
                                "另一模型"),
                        3));
    }

    @Test
    void 相同名称在两个租户内隔离且跨租户按标识不可见() {
        var store = repository.create(
                creation(STORE_TENANT, "provider-request-0002", "a".repeat(64), "通用模型"),
                3);
        repository.create(
                creation(CAMPUS_TENANT, "provider-request-0002", "a".repeat(64), "通用模型"),
                3);

        assertTrue(repository.findById(STORE_TENANT, store.connection().id()).isPresent());
        assertTrue(repository.findById(CAMPUS_TENANT, store.connection().id()).isEmpty());
        assertEquals(1, repository.findPage(STORE_TENANT, null, 20).items().size());
        assertEquals(1, repository.findPage(CAMPUS_TENANT, null, 20).items().size());
    }

    @Test
    void 探测事实使用乐观版本更新且只持久化密钥引用() {
        var created = repository.create(
                        creation(
                                STORE_TENANT,
                                "provider-request-0003",
                                "c".repeat(64),
                                "探测模型"),
                        3)
                .connection();
        var verified = created.recordSuccessfulProbe(
                "DETERMINISTIC_OK",
                CREATED_AT.plusSeconds(1));

        assertTrue(repository.update(verified, 0));
        assertFalse(repository.update(verified, 0));
        var restored = repository.findById(STORE_TENANT, created.id()).orElseThrow();
        assertEquals(true, restored.lastProbeSucceeded());
        assertEquals("secret://demo/provider", jdbc.queryForObject(
                """
                SELECT credential_reference
                FROM model_runtime.provider_connection
                WHERE tenant_id = ? AND id = ?
                """,
                String.class,
                STORE_TENANT.value(),
                created.id().value()));
    }

    @Test
    void 租户配额和畸形游标应被拒绝() {
        repository.create(
                creation(STORE_TENANT, "provider-request-0004", "d".repeat(64), "模型一"),
                1);

        assertThrows(
                ProviderConnectionQuotaExceededException.class,
                () -> repository.create(
                        creation(
                                STORE_TENANT,
                                "provider-request-0005",
                                "e".repeat(64),
                                "模型二"),
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findPage(STORE_TENANT, "不是有效游标", 20));
    }

    @Test
    void 同租户同名连接应产生稳定冲突() {
        repository.create(
                creation(STORE_TENANT, "provider-request-0006", "f".repeat(64), "重名模型"),
                3);

        assertThrows(
                ProviderConnectionNameConflictException.class,
                () -> repository.create(
                        creation(
                                STORE_TENANT,
                                "provider-request-0007",
                                "0".repeat(64),
                                "重名模型"),
                        3));
    }

    private static ProviderConnectionCreation creation(
            TenantId tenantId,
            String idempotencyKey,
            String fingerprint,
            String displayName) {
        return new ProviderConnectionCreation(
                ProviderConnection.create(
                        tenantId,
                        new ProviderConnectionId(IDENTIFIERS.incrementAndGet()),
                        ProviderKind.DETERMINISTIC,
                        displayName,
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new SecretReference("secret://demo/provider"),
                        CREATED_AT),
                idempotencyKey,
                fingerprint);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.adapter.out.persistence")
    static class TestApplication {

        @Bean
        ProviderConnectionRepository providerConnectionRepository(
                ProviderConnectionMapper mapper,
                PlatformTransactionManager transactions) {
            return new PostgresProviderConnectionRepository(mapper, transactions);
        }
    }
}
