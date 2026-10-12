package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerification;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(classes = PostgresPlatformModelDeploymentRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresPlatformModelDeploymentRepositoryTest {
    private static final AtomicLong IDENTIFIERS = new AtomicLong(330000000000000100L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T04:30:00Z");
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("foundation_test").withUsername("foundation").withPassword("foundation_test_only");
    @Autowired private PlatformModelDeploymentRepository deployments;
    @Autowired private PlatformModelDeploymentVerificationStore verificationStore;
    @Autowired private PlatformProviderConnectionRepository connections;
    @Autowired private JdbcTemplate jdbc;
    private PlatformProviderConnectionId connectionId;

    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach void clearFacts() {
        jdbc.update("DELETE FROM model_runtime.platform_model_capability_verification");
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment_request");
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment");
        jdbc.update("DELETE FROM model_runtime.platform_discovered_model");
        jdbc.update("DELETE FROM model_runtime.platform_provider_model_discovery");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_credential");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection");
        connectionId = new PlatformProviderConnectionId(IDENTIFIERS.incrementAndGet());
        connections.create(new PlatformProviderConnectionCreation(PlatformProviderConnection.create(
                connectionId,
                ProviderKind.DETERMINISTIC,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "平台连接" + connectionId.value(),
                URI.create("memory://deterministic"),
                "local",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/" + connectionId.value()),
                CREATED_AT),
                "platform-connection-" + connectionId.value(), "a".repeat(64)));
    }

    @Test void 部署和幂等事实只写入平台表且不带租户() {
        var first = deployments.create(creation("platform-deployment-request-01", "b".repeat(64), "平台嵌入模型"));
        var replayed = deployments.create(creation("platform-deployment-request-01", "b".repeat(64), "平台嵌入模型"));
        assertFalse(first.replayed());
        assertTrue(replayed.replayed());
        assertEquals(first.deployment().id(), replayed.deployment().id());
        assertEquals("PLATFORM", jdbc.queryForObject("SELECT scope FROM model_runtime.platform_model_deployment WHERE id = ?", String.class, first.deployment().id().value()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.model_deployment", Long.class));
    }

    @Test void 同幂等键不同请求和全局重名均失败关闭() {
        deployments.create(creation("platform-deployment-request-02", "b".repeat(64), "平台嵌入模型"));
        assertThrows(ModelDeploymentIdempotencyConflictException.class,
                () -> deployments.create(creation("platform-deployment-request-02", "c".repeat(64), "另一模型")));
        assertThrows(ModelDeploymentNameConflictException.class,
                () -> deployments.create(creation("platform-deployment-request-03", "d".repeat(64), "平台嵌入模型")));
    }

    @Test void 成功验证与部署修订原子提交且版本冲突回滚验证事实() {
        var created = deployments.create(creation("platform-deployment-request-04", "e".repeat(64), "验证模型")).deployment();
        var result = PlatformModelDeploymentVerification.verify(created,
                new PlatformModelCapabilityVerificationId(IDENTIFIERS.incrementAndGet()),
                new ModelCapabilityObservation(true, Set.of(ModelCapability.EMBEDDING), "DETERMINISTIC_EMBEDDING_OK",
                        Duration.ZERO, CREATED_AT.plusSeconds(1)), CREATED_AT.plusSeconds(3600));

        verificationStore.commit(result, created.version());

        assertEquals("VERIFIED", jdbc.queryForObject("SELECT status FROM model_runtime.platform_model_deployment WHERE id = ?",
                String.class, created.id().value()));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM model_runtime.platform_model_deployment WHERE id = ?",
                Long.class, created.id().value()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_model_capability_verification WHERE platform_model_deployment_id = ?",
                Long.class, created.id().value()));
        var conflicting = PlatformModelDeploymentVerification.verify(created,
                new PlatformModelCapabilityVerificationId(IDENTIFIERS.incrementAndGet()),
                new ModelCapabilityObservation(true, Set.of(ModelCapability.EMBEDDING), "DETERMINISTIC_EMBEDDING_OK",
                        Duration.ZERO, CREATED_AT.plusSeconds(2)), CREATED_AT.plusSeconds(3600));
        assertThrows(IllegalStateException.class, () -> verificationStore.commit(conflicting, created.version()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_model_capability_verification WHERE platform_model_deployment_id = ?",
                Long.class, created.id().value()));
    }

    private PlatformModelDeploymentCreation creation(String idempotencyKey, String fingerprint, String displayName) {
        return new PlatformModelDeploymentCreation(PlatformModelDeployment.create(new PlatformModelDeploymentId(IDENTIFIERS.incrementAndGet()),
                connectionId, 0, "deterministic-embedding", displayName, ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING), 8192, null, 1536, CREATED_AT), idempotencyKey, fingerprint);
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan(basePackageClasses = com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class)
    static class TestApplication {
        @Bean PlatformProviderConnectionRepository platformProviderConnectionRepository(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper requests, PlatformTransactionManager transactions) { return new PostgresPlatformProviderConnectionRepository(mapper, requests, transactions); }
        @Bean PlatformModelDeploymentRepository platformModelDeploymentRepository(JdbcTemplate jdbc, PlatformTransactionManager transactions) { return new PostgresPlatformModelDeploymentRepository(jdbc, transactions); }
        @Bean PlatformModelDeploymentVerificationStore platformModelDeploymentVerificationStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) { return new PostgresPlatformModelDeploymentVerificationStore(jdbc, transactions); }
    }
}
