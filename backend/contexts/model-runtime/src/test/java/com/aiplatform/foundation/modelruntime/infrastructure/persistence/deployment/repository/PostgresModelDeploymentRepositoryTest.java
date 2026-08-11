package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.repository;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelCapabilityVerificationMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentQuotaExceededException;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.deployment.operation.ModelDeploymentVerificationResult;
import com.aiplatform.foundation.modelruntime.domain.deployment.service.ModelDeploymentVerification;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.query.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.deployment.service.ModelDeploymentService;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionRequestMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository.PostgresProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

@SpringBootTest(classes = PostgresModelDeploymentRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresModelDeploymentRepositoryTest {

    private static final TenantId STORE_TENANT =
            new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT =
            new TenantId(100000000000000002L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-27T05:00:00Z");
    private static final ProviderConnectionId STORE_PROVIDER = new ProviderConnectionId(
            310000000000000001L);
    private static final ProviderConnectionId CAMPUS_PROVIDER = new ProviderConnectionId(
            310000000000000002L);
    private static final AtomicLong IDENTIFIERS =
            new AtomicLong(320000000000000100L);

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

    @Autowired
    private ModelDeploymentVerificationStore verificationStore;

    @Autowired
    private ProviderConnectionRepository providers;

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
        jdbc.execute("""
                TRUNCATE TABLE model_runtime.model_deployment_request,
                    model_runtime.model_capability_verification,
                    model_runtime.model_deployment
                """);
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
        assertEquals(1, repository.findPage(STORE_TENANT, 1, 20).items().size());
        assertEquals(1, repository.findPage(CAMPUS_TENANT, 1, 20).items().size());
    }

    @Test
    void 验证事实与状态迁移原子提交且供应商修订保持冻结() {
        var created = repository.create(
                        creation(
                                STORE_TENANT,
                                STORE_PROVIDER,
                                "deployment-request-0003",
                                "d".repeat(64),
                                "发布模型"),
                        6)
                .deployment();
        ModelDeploymentVerificationResult verified = ModelDeploymentVerification.verify(
                created,
                new ModelCapabilityVerificationId(330000000000000001L),
                new ModelCapabilityObservation(
                        true,
                        Set.of(ModelCapability.CHAT),
                        "DETERMINISTIC_OK",
                        Duration.ofMillis(10),
                        CREATED_AT),
                CREATED_AT.plusSeconds(86_400));

        verificationStore.commit(verified, 0);
        assertThrows(IllegalStateException.class, () -> verificationStore.commit(verified, 0));
        var restored = repository.findById(STORE_TENANT, created.id()).orElseThrow();
        assertEquals(1, restored.providerConnectionRevision());
        assertEquals(Set.of(ModelCapability.CHAT), restored.capabilities());
        assertEquals(verified.verification().id(), restored.lastVerificationId());
        assertEquals(1, restored.version());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM model_runtime.model_capability_verification",
                Integer.class));
    }

    @Test
    void 真实PostgreSQL连接修订漂移只改变部署可用性投影() {
        var created = repository.create(
                        creation(
                                STORE_TENANT,
                                STORE_PROVIDER,
                                "deployment-request-availability",
                                "1".repeat(64),
                                "可用性模型"),
                        6)
                .deployment();
        var verification = ModelDeploymentVerification.verify(
                created,
                new ModelCapabilityVerificationId(330000000000000002L),
                new ModelCapabilityObservation(
                        true,
                        Set.of(ModelCapability.CHAT),
                        "DETERMINISTIC_OK",
                        Duration.ofMillis(10),
                        CREATED_AT),
                CREATED_AT.plusSeconds(86_400));
        verificationStore.commit(verification, 0);
        var verified = repository.findById(STORE_TENANT, created.id()).orElseThrow();
        assertTrue(repository.update(verified.publish(CREATED_AT.plusSeconds(1)), 1));
        var service = availabilityService();

        var healthy = service.list(new ListModelDeploymentsQuery(
                trustedContext(), new PageQuery(1, 20)));
        assertEquals(ModelDeploymentAvailability.HEALTHY, healthy.items().getFirst().availability());
        assertEquals("PUBLISHED", healthy.items().getFirst().status());

        jdbc.update(
                "UPDATE model_runtime.provider_connection SET version = 2 WHERE tenant_id = ? AND id = ?",
                STORE_TENANT.value(), STORE_PROVIDER.value());

        var unavailable = service.list(new ListModelDeploymentsQuery(
                trustedContext(), new PageQuery(1, 20)));
        assertEquals(ModelDeploymentAvailability.UNAVAILABLE, unavailable.items().getFirst().availability());
        assertEquals("PUBLISHED", unavailable.items().getFirst().status());
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
                () -> repository.findPage(STORE_TENANT, 0, 20));
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
                        new ModelDeploymentId(IDENTIFIERS.incrementAndGet()),
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

    private ModelDeploymentService availabilityService() {
        var clock = Clock.fixed(CREATED_AT.plusSeconds(2), ZoneOffset.UTC);
        ProviderModelDiscoveryLookup discoveries =
                (tenantId, providerId, revision, modelCode, modelType, now) -> Optional.empty();
        return new ModelDeploymentService(
                repository,
                providers,
                discoveries,
                new ProbeProviderService(List.of(), clock),
                verificationStore,
                clock,
                () -> 320000000000000999L,
                6,
                Duration.ofHours(24));
    }

    private static TrustedRequestContext trustedContext() {
        return new TrustedRequestContext(
                STORE_TENANT,
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("model:read")),
                "trace-deployment-availability");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.modelruntime.infrastructure.persistence")
    static class TestApplication {

        @Bean
        ModelDeploymentRepository modelDeploymentRepository(
                ModelDeploymentMapper mapper,
                ModelDeploymentRequestMapper requestMapper,
                PlatformTransactionManager transactions) {
            return new PostgresModelDeploymentRepository(
                    mapper,
                    requestMapper,
                    transactions);
        }

        @Bean
        ModelDeploymentVerificationStore modelDeploymentVerificationStore(
                ModelDeploymentMapper deployments,
                ModelCapabilityVerificationMapper verifications,
                PlatformTransactionManager transactions) {
            return new PostgresModelDeploymentVerificationStore(
                    deployments, verifications, transactions);
        }

        @Bean
        ProviderConnectionRepository providerConnectionRepository(
                ProviderConnectionMapper mapper,
                ProviderConnectionRequestMapper requestMapper,
                PlatformTransactionManager transactions) {
            return new PostgresProviderConnectionRepository(mapper, requestMapper, transactions);
        }
    }
}
