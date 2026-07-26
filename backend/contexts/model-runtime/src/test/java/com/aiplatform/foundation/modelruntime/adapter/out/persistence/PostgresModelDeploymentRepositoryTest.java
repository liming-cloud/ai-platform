package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.catalog.ModelDeploymentQuotaExceededException;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
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

@SpringBootTest(classes = PostgresModelDeploymentRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresModelDeploymentRepositoryTest {

    private static final TenantId STORE_TENANT =
            TenantId.parse("10000000-0000-0000-0000-000000000001");
    private static final TenantId CAMPUS_TENANT =
            TenantId.parse("10000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T05:00:00Z");
    private static final ProviderConnectionId STORE_PROVIDER = new ProviderConnectionId(
            UUID.fromString("41000000-0000-0000-0000-000000000001"));
    private static final ProviderConnectionId CAMPUS_PROVIDER = new ProviderConnectionId(
            UUID.fromString("41000000-0000-0000-0000-000000000002"));

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private ModelDeploymentRepository repository;

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
    void clearAndSeedProviders() {
        jdbc.update("DELETE FROM model_runtime.model_deployment_request");
        jdbc.update("DELETE FROM model_runtime.model_deployment");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        seedProvider(STORE_TENANT, STORE_PROVIDER);
        seedProvider(CAMPUS_TENANT, CAMPUS_PROVIDER);
    }

    @Test
    void 同租户同键同请求重放且同键异参失败() {
        var first = repository.create(
                creation(
                        STORE_TENANT,
                        STORE_PROVIDER,
                        "deployment-request-0001",
                        "a".repeat(64),
                        "门店对话模型"),
                6);
        var replayed = repository.create(
                creation(
                        STORE_TENANT,
                        STORE_PROVIDER,
                        "deployment-request-0001",
                        "a".repeat(64),
                        "门店对话模型"),
                6);

        assertFalse(first.replayed());
        assertTrue(replayed.replayed());
        assertEquals(first.deployment().id(), replayed.deployment().id());
        assertThrows(
                ModelDeploymentIdempotencyConflictException.class,
                () -> repository.create(
                        creation(
                                STORE_TENANT,
                                STORE_PROVIDER,
                                "deployment-request-0001",
                                "b".repeat(64),
                                "另一模型"),
                        6));
    }

    @Test
    void 相同名称可跨租户存在且标识不可跨租户读取() {
        var store = repository.create(
                creation(
                        STORE_TENANT,
                        STORE_PROVIDER,
                        "deployment-request-0002",
                        "c".repeat(64),
                        "通用对话模型"),
                6);
        repository.create(
                creation(
                        CAMPUS_TENANT,
                        CAMPUS_PROVIDER,
                        "deployment-request-0002",
                        "c".repeat(64),
                        "通用对话模型"),
                6);

        assertTrue(repository.findById(STORE_TENANT, store.deployment().id()).isPresent());
        assertTrue(repository.findById(CAMPUS_TENANT, store.deployment().id()).isEmpty());
        assertEquals(1, repository.findPage(STORE_TENANT, null, 20).items().size());
        assertEquals(1, repository.findPage(CAMPUS_TENANT, null, 20).items().size());
    }

    @Test
    void 状态迁移使用乐观锁且供应商修订保持冻结() {
        var created = repository.create(
                        creation(
                                STORE_TENANT,
                                STORE_PROVIDER,
                                "deployment-request-0003",
                                "d".repeat(64),
                                "发布模型"),
                        6)
                .deployment();
        var verified = created.verify();

        assertTrue(repository.update(verified, 0));
        assertFalse(repository.update(verified, 0));
        var restored = repository.findById(STORE_TENANT, created.id()).orElseThrow();
        assertEquals(1, restored.providerConnectionRevision());
        assertEquals(Set.of(ModelCapability.CHAT), restored.capabilities());
        assertEquals(1, restored.version());
    }

    @Test
    void 租户配额同名约束和畸形游标均稳定拒绝() {
        repository.create(
                creation(
                        STORE_TENANT,
                        STORE_PROVIDER,
                        "deployment-request-0004",
                        "e".repeat(64),
                        "限额模型"),
                1);

        assertThrows(
                ModelDeploymentQuotaExceededException.class,
                () -> repository.create(
                        creation(
                                STORE_TENANT,
                                STORE_PROVIDER,
                                "deployment-request-0005",
                                "f".repeat(64),
                                "第二模型"),
                        1));
        assertThrows(
                ModelDeploymentNameConflictException.class,
                () -> repository.create(
                        creation(
                                STORE_TENANT,
                                STORE_PROVIDER,
                                "deployment-request-0006",
                                "0".repeat(64),
                                "限额模型"),
                        6));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findPage(STORE_TENANT, "不是有效游标", 20));
    }

    private void seedProvider(TenantId tenantId, ProviderConnectionId providerId) {
        jdbc.update(
                """
                INSERT INTO model_runtime.provider_connection (
                    tenant_id, id, provider_kind, display_name, endpoint, region,
                    authentication_type, credential_reference, status, version,
                    last_probe_succeeded, last_probe_code, last_probed_at, created_at
                ) VALUES (?, ?, 'DETERMINISTIC', ?, 'memory://deterministic', '本地',
                    'API_KEY', 'secret://demo/provider', 'VERIFIED', 1,
                    TRUE, 'DETERMINISTIC_OK', ?, ?)
                """,
                tenantId.value(),
                providerId.value(),
                "供应商-" + providerId.value(),
                Timestamp.from(CREATED_AT.minusSeconds(30)),
                Timestamp.from(CREATED_AT.minusSeconds(60)));
    }

    private static ModelDeploymentCreation creation(
            TenantId tenantId,
            ProviderConnectionId providerId,
            String idempotencyKey,
            String fingerprint,
            String displayName) {
        return new ModelDeploymentCreation(
                ModelDeployment.create(
                        tenantId,
                        new ModelDeploymentId(UUID.randomUUID()),
                        providerId,
                        1,
                        "deterministic-chat",
                        displayName,
                        ModelType.CHAT,
                        Set.of(ModelCapability.CHAT),
                        8192,
                        1024,
                        null,
                        CREATED_AT),
                idempotencyKey,
                fingerprint);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.adapter.out.persistence")
    static class TestApplication {

        @Bean
        ModelDeploymentRepository modelDeploymentRepository(
                ModelDeploymentMapper mapper,
                PlatformTransactionManager transactions) {
            return new PostgresModelDeploymentRepository(mapper, transactions);
        }
    }
}
