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
    private static final java.util.concurrent.atomic.AtomicBoolean ACTIVITY_FACTS_AVAILABLE = new java.util.concurrent.atomic.AtomicBoolean(true);
    @Autowired private JdbcTemplate jdbc;
    private PlatformProviderConnectionId connectionId;

    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach void clearFacts() {
        ACTIVITY_FACTS_AVAILABLE.set(true);
        jdbc.update("UPDATE model_runtime.platform_model_deployment SET last_verification_id = NULL, verification_expires_at = NULL, status = 'DRAFT'");
        jdbc.update("DELETE FROM model_runtime.platform_model_capability_verification");
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment_request");
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment");
        jdbc.update("DELETE FROM model_runtime.platform_model_candidate_confirmation");
        jdbc.update("DELETE FROM model_runtime.platform_discovered_model");
        jdbc.update("DELETE FROM model_runtime.platform_provider_model_discovery");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_credential");
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_publication");
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
        jdbc.update("UPDATE model_runtime.platform_provider_connection SET status = 'VERIFIED', version = 1, last_probe_succeeded = TRUE, last_probe_code = 'SYNTHETIC_OK', last_probed_at = ? WHERE id = ?",
                java.sql.Timestamp.from(CREATED_AT), connectionId.value());
        jdbc.update("INSERT INTO model_runtime.platform_provider_model_discovery VALUES (?, ?, 1, TRUE, 'SYNTHETIC_OK', 0, ?, ?)",
                connectionId.value() + 1, connectionId.value(), java.sql.Timestamp.from(CREATED_AT), java.sql.Timestamp.from(CREATED_AT.plusSeconds(3600)));
        jdbc.update("INSERT INTO model_runtime.platform_discovered_model (discovery_id, model_code, model_type, capabilities, classification, display_name) VALUES (?, 'deterministic-embedding', 'EMBEDDING', ARRAY['EMBEDDING'], 'CONFIRMED_BY_PROVIDER', '合成向量模型')", connectionId.value() + 1);
        jdbc.update("INSERT INTO model_runtime.platform_provider_connection_publication VALUES (?, ?, 1, 1, 101, ?, ?)",
                connectionId.value() + 2, connectionId.value(), java.sql.Timestamp.from(CREATED_AT), java.sql.Timestamp.from(CREATED_AT));
    }

    @Test void 过期轮换和未发布来源提交前拒绝且不留下草稿() {
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_publication WHERE connection_id = ?", connectionId.value());
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class, () -> deployments.create(creation("source-gone-request-01", "f".repeat(64), "拒绝草稿")));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment_request", Long.class));
    }

    @Test void 不存在的活动租户不能留下草稿和幂等事实() {
        var creation = creation("tenant-gone-request-01", "f".repeat(64), "失效租户草稿");
        var original = creation.deployment();
        var input = original.inputs();
        var invalid = PlatformModelDeployment.createDraft(original.id(), connectionId, 1, original.modelCode(), original.displayName(),
                original.modelType(), original.capabilities(), original.contextWindow(), null, original.embeddingDimension(), CREATED_AT,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs(input.parameterSource(), input.residency(), null,
                        Set.of(999L), input.discoveryId(), input.candidateDisplayName(), input.classification(), 101L, CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> deployments.create(new PlatformModelDeploymentCreation(invalid, creation.idempotencyKey(), creation.requestFingerprint())));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment", Long.class));
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


    @Test void 精确回读完整声明且最新发现不再包含模型时不能回退旧根() {
        var created = deployments.create(creation("snapshot-request-01", "b".repeat(64), "完整声明部署")).deployment();
        var restored = deployments.findById(created.id()).orElseThrow();
        assertEquals(created.inputs(), restored.inputs());
        assertEquals(1L, deployments.findPage(1, 20).totalElements());
        assertEquals(created.id(), deployments.findPage(1, 20).items().getFirst().id());
        jdbc.update("INSERT INTO model_runtime.platform_provider_model_discovery VALUES (?, ?, 1, TRUE, 'SYNTHETIC_OK', 0, ?, ?)",
                connectionId.value() + 10, connectionId.value(), java.sql.Timestamp.from(CREATED_AT.plusSeconds(1)), java.sql.Timestamp.from(CREATED_AT.plusSeconds(3600)));
        assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class,
                () -> deployments.create(creation("snapshot-request-02", "c".repeat(64), "不可回退旧根")));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment", Long.class));
    }

    @Test void 未知分类和幂等审计与草稿失败时共同回滚() {
        jdbc.update("UPDATE model_runtime.platform_discovered_model SET model_type = NULL, capabilities = ARRAY[]::TEXT[], classification = 'REQUIRES_ADMIN_CONFIRMATION' WHERE discovery_id = ?", connectionId.value() + 1);
        var original = creation("audit-rollback-01", "a".repeat(64), "确认回滚部署").deployment();
        var i = original.inputs();
        var draft = PlatformModelDeployment.createDraft(original.id(), connectionId, 1, original.modelCode(), original.displayName(),
                original.modelType(), original.capabilities(), original.contextWindow(), null, original.embeddingDimension(), CREATED_AT,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs(i.parameterSource(), i.residency(), null,
                        i.plannedTenantIds(), i.discoveryId(), i.candidateDisplayName(),
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN,
                        101L, CREATED_AT));
        jdbc.execute("CREATE FUNCTION model_runtime.reject_synthetic_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic-audit-failure'; END $$");
        jdbc.execute("CREATE TRIGGER synthetic_request_failure BEFORE INSERT ON model_runtime.platform_model_deployment_request FOR EACH ROW EXECUTE FUNCTION model_runtime.reject_synthetic_request()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> deployments.create(new PlatformModelDeploymentCreation(draft, "audit-rollback-01", "a".repeat(64))));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_candidate_confirmation", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment_request", Long.class));
        } finally {
            jdbc.execute("DROP TRIGGER synthetic_request_failure ON model_runtime.platform_model_deployment_request");
            jdbc.execute("DROP FUNCTION model_runtime.reject_synthetic_request()");
        }
    }


    @Test void 创建边界缺少拥有者时拒绝且不产生任何事实() {
        ACTIVITY_FACTS_AVAILABLE.set(false);
        try {
            assertThrows(com.aiplatform.foundation.shared.kernel.error.ApplicationException.class,
                    () -> deployments.create(creation("missing-owner-01", "f".repeat(64), "缺少拥有者")));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment_request", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_candidate_confirmation", Long.class));
        } finally { ACTIVITY_FACTS_AVAILABLE.set(true); }
    }

    @Test void 无名称未知候选的分类声明和草稿可原子回读且不补造名称() {
        jdbc.update("UPDATE model_runtime.platform_discovered_model SET display_name = NULL, model_type = NULL, capabilities = ARRAY[]::TEXT[], classification = 'REQUIRES_ADMIN_CONFIRMATION' WHERE discovery_id = ?", connectionId.value() + 1);
        var d = creation("missing-name-01", "a".repeat(64), "缺名候选部署").deployment();
        var input = d.inputs();
        var draft = PlatformModelDeployment.createDraft(d.id(), connectionId, 1, d.modelCode(), d.displayName(), d.modelType(), d.capabilities(),
                d.contextWindow(), null, d.embeddingDimension(), CREATED_AT,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs(
                        input.parameterSource(), input.residency(), null, input.plannedTenantIds(), input.discoveryId(), null,
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN,
                        101L, CREATED_AT));
        var saved = deployments.create(new PlatformModelDeploymentCreation(draft, "missing-name-01", "a".repeat(64)));
        org.junit.jupiter.api.Assertions.assertNull(deployments.findById(saved.deployment().id()).orElseThrow().inputs().candidateDisplayName());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_discovered_model WHERE display_name IS NULL", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_candidate_confirmation", Long.class));
    }

    private PlatformModelDeploymentCreation creation(String idempotencyKey, String fingerprint, String displayName) {
        return new PlatformModelDeploymentCreation(PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(IDENTIFIERS.incrementAndGet()),
                connectionId, 1, "deterministic-embedding", displayName, ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING), 8192, null, 1536, CREATED_AT,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs("合成资料 v1", "合成区域", null,
                        Set.of(201L), connectionId.value() + 1, "合成向量模型",
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER,
                        101L, CREATED_AT)), idempotencyKey, fingerprint);
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan(basePackageClasses = com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class)
    static class TestApplication {
        @Bean com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor pagination() {
            var result = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            result.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.POSTGRE_SQL));
            return result;
        }
        @Bean PlatformProviderConnectionRepository platformProviderConnectionRepository(com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionRequestMapper requests, PlatformTransactionManager transactions) { return new PostgresPlatformProviderConnectionRepository(mapper, requests, transactions); }
        @Bean PlatformModelDeploymentRepository platformModelDeploymentRepository(
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentMapper mapper,
            com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentRequestMapper requests,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidates,
                PlatformProviderConnectionRepository connections, PlatformTransactionManager transactions) {
            var discoveries = new PostgresPlatformProviderModelDiscoveryStore(transactions, connections, candidates);
            return new PostgresPlatformModelDeploymentRepository(mapper, requests, transactions, discoveries, discoveries,
                    () -> {
                        if (!ACTIVITY_FACTS_AVAILABLE.get()) throw new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                                com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.RUNTIME_UNAVAILABLE);
                        return ids -> { if (!ids.equals(Set.of(201L))) throw new IllegalArgumentException("合成租户未激活"); };
                    },
                    java.time.Clock.fixed(CREATED_AT, java.time.ZoneOffset.UTC));
        }
        @Bean PlatformModelDeploymentVerificationStore platformModelDeploymentVerificationStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) { return new PostgresPlatformModelDeploymentVerificationStore(jdbc, transactions); }
    }
}
