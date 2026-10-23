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
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStateException;
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
    private static final java.util.concurrent.atomic.AtomicBoolean ACTOR_CURRENT = new java.util.concurrent.atomic.AtomicBoolean(true);
    private static final java.util.concurrent.atomic.AtomicReference<Instant> VERIFICATION_NOW = new java.util.concurrent.atomic.AtomicReference<>();
    private static final AtomicLong IDENTIFIERS = new AtomicLong(330000000000000100L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T04:30:00Z");
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("foundation_test").withUsername("foundation").withPassword("foundation_test_only");
    @Autowired private PlatformModelDeploymentRepository deployments;
    @Autowired private PlatformModelDeploymentVerificationStore verificationStore;
    @Autowired private PlatformProviderConnectionRepository connections;
    private static final java.util.concurrent.atomic.AtomicBoolean ACTIVITY_FACTS_AVAILABLE = new java.util.concurrent.atomic.AtomicBoolean(true);
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationAccessMapper configurationGrants;
    @Autowired private com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.publication.PlatformModelSupplyPublicationStore supply;
    @Autowired private com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository catalog;
    private static final java.util.concurrent.atomic.AtomicBoolean FEATURES_AVAILABLE = new java.util.concurrent.atomic.AtomicBoolean(true);
    private static final java.util.concurrent.atomic.AtomicReference<Set<Long>> ACTIVE_TENANTS =
            new java.util.concurrent.atomic.AtomicReference<>(Set.of(201L));
    private PlatformProviderConnectionId connectionId;

    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/model-runtime");
    }

    @BeforeEach void clearFacts() {
        FEATURES_AVAILABLE.set(true); ACTIVE_TENANTS.set(Set.of(201L));
        jdbc.update("DELETE FROM model_runtime.platform_model_supply_audit");
        jdbc.update("DELETE FROM model_runtime.platform_model_supply_grant");
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment_publication");
        jdbc.update("DELETE FROM model_runtime.model_configuration_access_grant");
        jdbc.update("DELETE FROM model_runtime.model_configuration_credential");
        jdbc.update("DELETE FROM model_runtime.model_configuration");
        jdbc.update("DELETE FROM model_runtime.controlled_model_catalog_entry");
        ACTIVITY_FACTS_AVAILABLE.set(true); ACTOR_CURRENT.set(true); VERIFICATION_NOW.set(CREATED_AT.plusSeconds(3));
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment_verification_audit");
        jdbc.update("DELETE FROM model_runtime.platform_capability_verification_reservation");
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
                new ModelCapabilityObservation(true, Set.of(ModelCapability.EMBEDDING), "S2_PASSED",
                        Duration.ZERO, CREATED_AT.plusSeconds(1)), CREATED_AT.plusSeconds(3600));

        var reservation = reservation(created);
        verificationStore.reserve(reservation, () -> true);
        verificationStore.commit(reservation, result, "PASSED", 3, reservation.expectedSampleDigests(), () -> true);

        assertEquals("VERIFIED", jdbc.queryForObject("SELECT status FROM model_runtime.platform_model_deployment WHERE id = ?",
                String.class, created.id().value()));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM model_runtime.platform_model_deployment WHERE id = ?",
                Long.class, created.id().value()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_model_capability_verification WHERE platform_model_deployment_id = ?",
                Long.class, created.id().value()));
        var conflicting = PlatformModelDeploymentVerification.verify(created,
                new PlatformModelCapabilityVerificationId(IDENTIFIERS.incrementAndGet()),
                new ModelCapabilityObservation(true, Set.of(ModelCapability.EMBEDDING), "S2_PASSED",
                        Duration.ZERO, CREATED_AT.plusSeconds(2)), CREATED_AT.plusSeconds(3600));
        assertThrows(IllegalStateException.class, () -> verificationStore.commit(reservation, conflicting, "PASSED", 3, reservation.expectedSampleDigests(), () -> true));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM model_runtime.platform_model_capability_verification WHERE platform_model_deployment_id = ?",
                Long.class, created.id().value()));
    }


    @Test void 精确回读完整声明且最新发现不再包含模型时不能回退旧根() {
        var created = deployments.create(creation("snapshot-request-01", "b".repeat(64), "完整声明部署")).deployment();
        var restored = deployments.findById(created.id()).orElseThrow();
        assertEquals(created.inputs(), restored.inputs());
        assertEquals(1L, deployments.findPage(1, 20, "").totalElements());
        assertEquals(created.id(), deployments.findPage(1, 20, "").items().getFirst().id());
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

    @Test void 名称搜索先筛选再分页且同时间按编号稳定排序() {
        var ids = new java.util.ArrayList<PlatformModelDeploymentId>();
        for (int i = 0; i < 21; i++) ids.add(deployments.create(creation("directory-search-" + i, "b".repeat(64), "北区部署" + i)).deployment().id());
        deployments.create(creation("directory-noise-01", "c".repeat(64), "其他部署"));
        var first = deployments.findPage(1, 10, "北区");
        var last = deployments.findPage(3, 10, "北区");
        assertEquals(21L, first.totalElements()); assertEquals(21L, last.totalElements());
        assertEquals(10, first.items().size()); assertEquals(1, last.items().size());
        assertEquals(ids.getLast(), first.items().getFirst().id());
        assertEquals(ids.getFirst(), last.items().getFirst().id());
        assertTrue(deployments.findPage(4, 10, "北区").items().isEmpty());
        assertEquals(21L, deployments.findPage(4, 10, "北区").totalElements());
        assertEquals(22L, deployments.counts().total());
    }

    @Test void 驻留和名称按字面不区分大小写且不搜索其他投影字段() {
        var first = deployments.create(creation("directory-literal-01", "d".repeat(64), "字面%_\\Alpha")).deployment();
        deployments.create(creation("directory-literal-02", "e".repeat(64), "另一普通部署"));
        jdbc.update("UPDATE model_runtime.platform_model_deployment SET residency = ? WHERE id = ?", "华北%_\\Local", first.id().value());
        for (var q : new String[]{"alpha", "ALPHA", "%_\\", "华北", "local"}) {
            var page = deployments.findPage(1, 20, q);
            assertEquals(q.equals("local") ? 2L : 1L, page.totalElements(), q);
            assertTrue(page.items().stream().anyMatch(item -> item.id().equals(first.id())));
        }
        for (var q : new String[]{"DRAFT", "EMBEDDING", "deterministic-embedding", "合成资料", "查看", "' OR 1=1 --", "不存在"})
            assertEquals(0L, deployments.findPage(1, 20, q).totalElements(), q);
        jdbc.update("UPDATE model_runtime.platform_model_deployment SET parameter_source = NULL, residency = NULL, planned_tenant_ids = NULL, candidate_discovery_id = NULL, candidate_display_name = NULL, candidate_classification = NULL, declared_by_subject_id = NULL, declared_at = NULL WHERE id = ?", first.id().value());
        assertEquals(0L, deployments.findPage(1, 20, "暂未取得").totalElements());
        assertEquals(0L, deployments.findPage(1, 20, "华北").totalElements());
    }

    @Test void 平台目录分页偏移有界且保留真实总数() {
        deployments.create(creation("directory-bound-01", "a".repeat(64), "边界部署"));
        assertEquals(1L, deployments.findPage(10001, 10, "").totalElements());
        assertThrows(IllegalArgumentException.class, () -> deployments.findPage(10002, 10, ""));
        assertThrows(IllegalArgumentException.class, () -> deployments.findPage(Long.MAX_VALUE, 100, ""));
    }

    private PlatformModelDeploymentCreation creation(String idempotencyKey, String fingerprint, String displayName) {
        return new PlatformModelDeploymentCreation(PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(IDENTIFIERS.incrementAndGet()),
                connectionId, 1, "deterministic-embedding", displayName, ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING), 8192, null, 1536, CREATED_AT,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs("合成资料 v1", "local", null,
                        Set.of(201L), connectionId.value() + 1, "合成向量模型",
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER,
                        101L, CREATED_AT)), idempotencyKey, fingerprint);
    }

    @Test void 同一部署只能存在一个活动预留且重复请求不会扩大预算() {
        var d = deployments.create(creation("reservation-unique-01", "d".repeat(64), "预留互斥模型")).deployment();
        var first = reservation(d); verificationStore.reserve(first, () -> true);
        var second = reservation(d);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> verificationStore.reserve(second, () -> true));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_capability_verification_reservation", Long.class));
        assertEquals(0L, verificationCount());
    }

    @Test void 来源轮换后迟到成功不得提交且未知用量不退还预留() {
        var d = deployments.create(creation("reservation-rotation-01", "d".repeat(64), "轮换拒绝模型")).deployment();
        var r = reservation(d); verificationStore.reserve(r, () -> true);
        jdbc.update("UPDATE model_runtime.platform_provider_connection SET version = 2 WHERE id = ?", connectionId.value());
        assertThrows(IllegalStateException.class, () -> verificationStore.commit(r, success(d, r), "PASSED", 3, r.expectedSampleDigests(), () -> true));
        assertEquals(0L, verificationCount()); assertEquals("DRAFT", status(d));
        assertEquals(0, new java.math.BigDecimal("0.01").compareTo(jdbc.queryForObject("SELECT reserved_fee FROM model_runtime.platform_capability_verification_reservation WHERE id = ?", java.math.BigDecimal.class, r.id())));
    }

    @Test void 撤销权限与退出会话都不能提交已收到的成功结果() {
        for (boolean revoke : java.util.List.of(true, false)) {
            var d = deployments.create(creation("reservation-session-" + revoke, "d".repeat(64), "会话拒绝模型" + revoke)).deployment();
            var r = reservation(d); verificationStore.reserve(r, () -> true);
            ACTOR_CURRENT.set(!revoke);
            assertThrows(com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class,
                    () -> verificationStore.commit(r, success(d, r), "PASSED", 3, r.expectedSampleDigests(), () -> revoke));
            ACTOR_CURRENT.set(true); assertEquals(0L, verificationCount()); assertEquals("DRAFT", status(d));
        }
    }

    @Test void 缺样本或超时的成功回执都不能提升状态() {
        var d = deployments.create(creation("reservation-late-01", "d".repeat(64), "迟到拒绝模型")).deployment();
        var r = reservation(d); verificationStore.reserve(r, () -> true);
        assertThrows(IllegalArgumentException.class, () -> verificationStore.commit(r, success(d, r), "PASSED", 2,
                r.expectedSampleDigests().subList(0, 2), () -> true));
        VERIFICATION_NOW.set(r.deadline().plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> verificationStore.commit(r, success(d, r), "PASSED", 3, r.expectedSampleDigests(), () -> true));
        assertEquals(0L, verificationCount()); assertEquals("DRAFT", status(d));
    }

    @Test void 审计失败回滚验证CAS和终态但保留外呼前预留() {
        var d = deployments.create(creation("reservation-audit-01", "d".repeat(64), "审计回滚模型")).deployment();
        var r = reservation(d); verificationStore.reserve(r, () -> true);
        jdbc.execute("ALTER TABLE model_runtime.platform_model_deployment_verification_audit ADD CONSTRAINT synthetic_reject_success CHECK (NOT succeeded)");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> verificationStore.commit(r, success(d, r), "PASSED", 3, r.expectedSampleDigests(), () -> true));
            assertEquals(0L, verificationCount()); assertEquals("DRAFT", status(d));
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM model_runtime.platform_capability_verification_reservation WHERE id = ?", String.class, r.id()));
        } finally { jdbc.execute("ALTER TABLE model_runtime.platform_model_deployment_verification_audit DROP CONSTRAINT synthetic_reject_success"); }
    }

    @Test void 任意三条摘要不能冒充固定安全样本证据() {
        var d = deployments.create(creation("reservation-manifest-01", "d".repeat(64), "样本清单模型")).deployment();
        var r = reservation(d);
        var fabricated = new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation(
                r.id(), r.actor(), d, r.targetFingerprint(), r.inputsDigest(), r.planFingerprint(), r.quoteDigest(), r.quoteExpiresAt(), r.sampleVersion(),
                java.util.List.of("1".repeat(64), "2".repeat(64), "3".repeat(64)), r.reservedFee(), r.startedAt(), r.deadline());
        assertThrows(IllegalArgumentException.class, () -> verificationStore.reserve(fabricated, () -> true));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_capability_verification_reservation", Long.class));
    }

    @Test void 第一请求前预留已提交且协议回调不持有数据库事务() {
        var d = deployments.create(creation("reservation-protocol-01", "d".repeat(64), "协议事务模型")).deployment();
        var actor = reservation(d).actor(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        var sample = new com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilitySampleExecutor() {
            public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKind() {
                return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.DETERMINISTIC;
            }
            public com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult execute(
                    com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest request) {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_capability_verification_reservation WHERE status = 'ACTIVE'", Long.class));
                calls.incrementAndGet();
                return new com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult(
                        request.binding(), request.type(), null,
                        java.util.List.of(new com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult.Embedding(
                            0, java.util.Collections.nCopies(d.embeddingDimension(), 1.0))), java.util.List.of(), 10L, 0L, false);
            }
        };
        var now = VERIFICATION_NOW.get();
        var price = new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationPrice(
                connectionId.value(), d.providerConnectionRevision(), d.modelCode(), d.modelType(), d.inputs().residency(), "a".repeat(64),
                now.minusSeconds(60), now.plusSeconds(600), "CNY", "PER_MILLION_TOKENS", java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, 1024, 1024);
        var service = new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelProbeProviderService(
                java.util.List.of(sample), java.util.List.of(), java.util.List.of(price), verificationStore,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC), IDENTIFIERS::incrementAndGet, Duration.ofHours(24));
        var result = service.probe(new com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand(
                actor, d.id().toString(), d.version(), Duration.ofSeconds(30), () -> true), d, connections.findById(connectionId).orElseThrow());
        assertTrue(result.succeeded()); assertEquals(3, calls.get()); assertEquals("VERIFIED", status(d));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment_verification_audit", Long.class));
    }

    private long verificationCount() { return jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_capability_verification", Long.class); }
    private String status(PlatformModelDeployment d) { return jdbc.queryForObject("SELECT status FROM model_runtime.platform_model_deployment WHERE id = ?", String.class, d.id().value()); }
    private static com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult success(
            PlatformModelDeployment d, com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation r) {
        return PlatformModelDeploymentVerification.verify(d, new PlatformModelCapabilityVerificationId(IDENTIFIERS.incrementAndGet()),
                new ModelCapabilityObservation(true, d.capabilities(), "S2_PASSED", Duration.ofMillis(1), r.startedAt().plusMillis(1)),
                r.startedAt().plusSeconds(3600));
    }

    private com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation reservation(PlatformModelDeployment deployment) {
        long id = IDENTIFIERS.incrementAndGet();
        var actor = new com.aiplatform.foundation.security.identity.PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(101), com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE, 1,
                Set.of(com.aiplatform.foundation.security.identity.PlatformPermissions.SESSION_ACCESS, com.aiplatform.foundation.security.identity.PlatformPermissions.MODEL_PLATFORM_WRITE), "synthetic-verification");
        var started = VERIFICATION_NOW.get().minusSeconds(2);
        return new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation(
                id, actor, deployment,
                com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelProbeProviderService.verificationTargetFingerprint(deployment, deployment.version()),
                com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelProbeProviderService.verificationInputsDigest(deployment), String.format("%064x", id), "c".repeat(64),
                started.plusSeconds(900), "s2-capability-safety-v1", com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformCapabilitySafetySuite.sampleDigests(deployment.modelType()),
                new java.math.BigDecimal("0.01"), started, started.plusSeconds(30));
    }

    @Test void 完整三样本后原子发布目录授权审计且重复发布不产生第二份() {
        var d = verifiedPublication("原子发布模型", false);
        var published = supply.publish(actor(), d, () -> true);
        assertEquals("PUBLISHED", status(d)); assertEquals(d.version() + 1, published.version());
        assertEquals(1L, count("controlled_model_catalog_entry"));
        assertEquals(1L, count("platform_model_deployment_publication"));
        assertEquals(1L, count("platform_model_supply_grant")); assertEquals(1L, count("platform_model_supply_audit"));
        assertEquals(0L, count("model_configuration"));
        var facts = supply.facts(d.id()).orElseThrow();
        assertEquals(1L, facts.activeGrantCount()); assertEquals(0L, facts.dependentConfigurationCount());
        assertThrows(IllegalStateException.class, () -> supply.publish(actor(), d, () -> true));
        assertEquals(1L, count("controlled_model_catalog_entry"));
    }

    @Test void 缺末尾验证审计或固定样本证明不能发布() {
        var d = verifiedPublication("缺失证明模型", false);
        jdbc.update("DELETE FROM model_runtime.platform_model_deployment_verification_audit");
        assertThrows(ModelDeploymentStateException.class, () -> supply.publish(actor(), d, () -> true));
        assertEquals(0L, count("controlled_model_catalog_entry")); assertEquals("VERIFIED", status(d));
    }

    @Test void 变更已验证声明不能复用旧结果发布() {
        var d = verifiedPublication("声明变化模型", false);
        jdbc.update("UPDATE model_runtime.platform_model_deployment SET context_window = context_window + 1 WHERE id = ?", d.id().value());
        var current = deployments.findById(d.id()).orElseThrow();
        assertThrows(ModelDeploymentStateException.class, () -> supply.publish(actor(), current, () -> true));
        assertEquals(0L, count("platform_model_supply_grant"));
    }

    @Test void 发布前权限会话来源发现根或租户失效均不能留下供应() {
        var d = verifiedPublication("发布失效模型", false);
        ACTOR_CURRENT.set(false);
        assertThrows(com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class, () -> supply.publish(actor(), d, () -> true));
        ACTOR_CURRENT.set(true);
        assertThrows(com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class, () -> supply.publish(actor(), d, () -> false));
        ACTIVE_TENANTS.set(Set.of());
        assertThrows(IllegalArgumentException.class, () -> supply.publish(actor(), d, () -> true));
        ACTIVE_TENANTS.set(Set.of(201L));
        jdbc.update("DELETE FROM model_runtime.platform_provider_connection_publication");
        assertThrows(IllegalStateException.class, () -> supply.publish(actor(), d, () -> true));
        assertEquals(0L, count("controlled_model_catalog_entry")); assertEquals(0L, count("platform_model_supply_grant"));
    }

    @Test void 发布审计失败原子回滚目录授权与部署CAS() {
        var d = verifiedPublication("发布审计回滚", false);
        jdbc.execute("ALTER TABLE model_runtime.platform_model_supply_audit ADD CONSTRAINT reject_publish_test CHECK (action <> 'PUBLISH')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> supply.publish(actor(), d, () -> true));
        } finally { jdbc.execute("ALTER TABLE model_runtime.platform_model_supply_audit DROP CONSTRAINT reject_publish_test"); }
        assertEquals("VERIFIED", status(d)); assertEquals(0L, count("controlled_model_catalog_entry"));
        assertEquals(0L, count("platform_model_deployment_publication")); assertEquals(0L, count("platform_model_supply_grant"));
    }

    @Test void 真正发布供应按租户分页撤权和停用即时阻断且保留历史() {
        ACTIVE_TENANTS.set(Set.of(201L, 202L));
        var d = verifiedPublication("当前供应模型", true);
        var published = supply.publish(actor(), d, () -> true);
        var tenant = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L);
        var other = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(202L);
        var entry = new com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId(
                Long.parseLong(supply.facts(d.id()).orElseThrow().catalogEntryId()));
        assertEquals(1L, catalog.findPublishedCatalogPage(tenant, 1, 1).totalElements());
        assertEquals(0L, catalog.findPublishedCatalogPage(other, 1, 1).totalElements());
        assertTrue(catalog.isSupplyAvailable(tenant, entry)); assertFalse(catalog.isSupplyAvailable(other, entry));
        assertThrows(ModelDeploymentStateException.class, () -> supply.revoke(actor(), d.id(), 202L, published.version(), 0, () -> true));
        supply.revoke(actor(), d.id(), 201L, published.version(), 0, () -> true);
        assertFalse(catalog.isSupplyAvailable(tenant, entry));
        assertEquals(0L, catalog.findPublishedCatalogPage(tenant, 1, 1).totalElements());
        assertThrows(ModelDeploymentStateException.class, () -> supply.revoke(actor(), d.id(), 201L, published.version(), 0, () -> true));
        assertEquals(1L, supply.facts(d.id()).orElseThrow().revokedGrantCount());
        supply.disable(actor(), published, () -> true);
        assertEquals("DISABLED", status(d)); assertEquals(1L, count("platform_model_deployment_publication"));
        assertEquals(1L, count("platform_model_supply_grant")); assertEquals(3L, count("platform_model_supply_audit"));
    }

    @Test void 当前来源轮换和验证过期不把历史发布视作当前可用() {
        var d = verifiedPublication("来源变化供应", true);
        supply.publish(actor(), d, () -> true);
        var tenant = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L);
        assertEquals(1L, catalog.findPublishedCatalogPage(tenant, 1, 20).totalElements());
        jdbc.update("UPDATE model_runtime.platform_provider_connection SET version = version + 1 WHERE id = ?", connectionId.value());
        assertEquals(0L, catalog.findPublishedCatalogPage(tenant, 1, 20).totalElements());
        assertEquals(1L, count("platform_model_deployment_publication"));
    }

    @Test void 撤权审计失败回滚授权且停用不删除租户配置() {
        var d = verifiedPublication("撤权审计模型", true);
        var published = supply.publish(actor(), d, () -> true);
        long entry = Long.parseLong(supply.facts(d.id()).orElseThrow().catalogEntryId());
        jdbc.update("INSERT INTO model_runtime.model_configuration VALUES (?, 'TENANT', 201, NULL, '合成配置', ?, 'DRAFT', 0, ?)",
                IDENTIFIERS.incrementAndGet(), entry, java.sql.Timestamp.from(VERIFICATION_NOW.get()));
        jdbc.execute("ALTER TABLE model_runtime.platform_model_supply_audit ADD CONSTRAINT reject_revoke_test CHECK (action <> 'REVOKE')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> supply.revoke(actor(), d.id(), 201L, published.version(), 0, () -> true));
        } finally { jdbc.execute("ALTER TABLE model_runtime.platform_model_supply_audit DROP CONSTRAINT reject_revoke_test"); }
        assertEquals(1L, supply.facts(d.id()).orElseThrow().activeGrantCount());
        assertEquals(1L, supply.facts(d.id()).orElseThrow().dependentConfigurationCount());
        supply.disable(actor(), published, () -> true);
        assertEquals(1L, count("model_configuration")); assertEquals(1L, count("controlled_model_catalog_entry"));
        assertEquals(0L, catalog.findPublishedCatalogPage(new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L), 1, 20).totalElements());
    }

    @Test void 并发发布只有一个胜者且目录授权与审计各一份() throws Exception {
        var d = verifiedPublication("并发发布模型", false);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
                start.await();
                try { supply.publish(actor(), d, () -> true); return true; }
                catch (IllegalStateException expectedConflict) { return false; }
            }));
            start.countDown();
            int successes = 0; for (var future : futures) if (future.get()) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1L, count("controlled_model_catalog_entry")); assertEquals(1L, count("platform_model_supply_grant"));
        assertEquals(1L, count("platform_model_supply_audit"));
    }

    @Test void 固定摘要被替换成任意三项也不能发布() {
        var d = verifiedPublication("伪造三样本模型", false);
        var fake = "a".repeat(64) + "," + "b".repeat(64) + "," + "c".repeat(64);
        jdbc.update("UPDATE model_runtime.platform_capability_verification_reservation SET expected_sample_digests = string_to_array(?, ','), passed_sample_digests = string_to_array(?, ',')", fake, fake);
        assertThrows(ModelDeploymentStateException.class, () -> supply.publish(actor(), d, () -> true));
        assertEquals(0L, count("platform_model_deployment_publication"));
    }

    @Test void 重排发布目录正确往返且不会变成嵌入能力() {
        jdbc.update("INSERT INTO model_runtime.platform_discovered_model (discovery_id, model_code, model_type, capabilities, classification, display_name) VALUES (?, 'synthetic-rerank', 'RERANK', ARRAY['RERANK'], 'CONFIRMED_BY_PROVIDER', '合成重排模型')", connectionId.value() + 1);
        var input = new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs(
                "合成资料 v1", "local", 3, Set.of(201L), connectionId.value() + 1, "合成重排模型",
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER,
                101L, CREATED_AT);
        var draft = PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(IDENTIFIERS.incrementAndGet()), connectionId, 1,
                "synthetic-rerank", "重排发布模型", ModelType.RERANK, Set.of(ModelCapability.RERANK), 8192, null, null, CREATED_AT, input);
        var d = deployments.create(new PlatformModelDeploymentCreation(draft, "rerank-publication-01", "e".repeat(64))).deployment();
        var r = reservation(d); verificationStore.reserve(r, () -> true);
        verificationStore.commit(r, success(d, r), "PASSED", 3, r.expectedSampleDigests(), () -> true);
        supply.publish(actor(), deployments.findById(d.id()).orElseThrow(), () -> true);
        var entry = catalog.findCatalogEntry(new com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId(
                Long.parseLong(supply.facts(d.id()).orElseThrow().catalogEntryId()))).orElseThrow();
        assertEquals(ModelType.RERANK, entry.modelType()); assertEquals(3, entry.capabilities().rerankCandidateLimit());
        org.junit.jupiter.api.Assertions.assertNull(entry.capabilities().embeddingDimension());
        org.junit.jupiter.api.Assertions.assertNull(entry.capabilities().outputLimit());
    }

    @Test void 只读供应不允许停用租户或新发现根继续使用旧授权() {
        var d = verifiedPublication("实时事实供应", true);
        supply.publish(actor(), d, () -> true);
        var tenant = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L);
        assertEquals(1L, catalog.findPublishedCatalogPage(tenant, 1, 20).totalElements());
        ACTIVE_TENANTS.set(Set.of());
        assertEquals(0L, catalog.findPublishedCatalogPage(tenant, 1, 20).totalElements());
        ACTIVE_TENANTS.set(Set.of(201L));
        jdbc.update("INSERT INTO model_runtime.platform_provider_model_discovery VALUES (?, ?, 1, TRUE, 'SYNTHETIC_OK', 0, ?, ?)",
                IDENTIFIERS.incrementAndGet(), connectionId.value(), java.sql.Timestamp.from(VERIFICATION_NOW.get().plusSeconds(1)),
                java.sql.Timestamp.from(VERIFICATION_NOW.get().plusSeconds(3600)));
        assertEquals(0L, catalog.findPublishedCatalogPage(tenant, 1, 20).totalElements());
        assertEquals(1L, count("platform_model_supply_grant"));
    }

    @Test void 既有配置保存启用和授权提交不能穿越供应撤权() {
        var d = verifiedPublication("受控配置供应", true);
        var published = supply.publish(actor(), d, () -> true);
        var tenant = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L);
        var entry = new com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId(
                Long.parseLong(supply.facts(d.id()).orElseThrow().catalogEntryId()));
        var config = com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration.create(
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId(IDENTIFIERS.incrementAndGet()),
                com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope.TENANT,
                tenant, null, "合成受控配置", entry, VERIFICATION_NOW.get());
        assertThrows(IllegalStateException.class, () -> catalog.lockSupply(tenant, entry));
        catalog.saveWithSupply(tenant, config);
        var access = new com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.PostgresModelConfigurationAccessRepository(
                configurationGrants, catalog, transactionManager);
        access.save(com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant.grant(
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId(IDENTIFIERS.incrementAndGet()),
                config.id(), tenant, null, VERIFICATION_NOW.get()));
        supply.revoke(actor(), d.id(), tenant.value(), published.version(), 0, () -> true);
        assertThrows(IllegalStateException.class, () -> catalog.activateWithSupply(tenant, config.activate()));
        assertEquals("DRAFT", catalog.findConfiguration(config.id()).orElseThrow().status().name());
        var second = com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration.create(
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId(IDENTIFIERS.incrementAndGet()),
                com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope.TENANT,
                tenant, null, "撤权后拒绝的新配置", entry, VERIFICATION_NOW.get());
        assertThrows(IllegalStateException.class, () -> catalog.saveWithSupply(tenant, second));
        assertThrows(IllegalStateException.class, () -> access.save(com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant.grant(
                new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId(IDENTIFIERS.incrementAndGet()),
                config.id(), tenant, new com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId(301L), VERIFICATION_NOW.get())));
        assertEquals(1L, count("model_configuration"));
    }

    @Test void 模型功能关闭使发布与新使用失败关闭而不删除既有事实() {
        var d = verifiedPublication("功能关闭供应", true);
        FEATURES_AVAILABLE.set(false);
        assertThrows(IllegalStateException.class, () -> supply.publish(actor(), d, () -> true));
        assertEquals(0L, count("platform_model_supply_grant"));
        FEATURES_AVAILABLE.set(true); supply.publish(actor(), d, () -> true);
        FEATURES_AVAILABLE.set(false);
        assertThrows(IllegalStateException.class, () -> catalog.findPublishedCatalogPage(new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L), 1, 20));
        assertEquals(1L, count("platform_model_deployment_publication"));
    }

    @Test void 发布提交前发现已到期不能留下活动授权() {
        var d = verifiedPublication("迟到发布模型", false);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IllegalStateException.class, () -> supply.publish(actor(), d, () -> {
            if (calls.incrementAndGet() == 2) VERIFICATION_NOW.set(CREATED_AT.plusSeconds(3600));
            return true;
        }));
        assertEquals("VERIFIED", status(d)); assertEquals(0L, count("platform_model_supply_grant"));
    }

    @Test void 多个独立部署可使用同一模型且租户目录分页与总数一致() {
        var first = verifiedPublication("分页供应甲", true); supply.publish(actor(), first, () -> true);
        var second = verifiedPublication("分页供应乙", false); supply.publish(actor(), second, () -> true);
        var tenant = new com.aiplatform.foundation.shared.kernel.tenant.TenantId(201L);
        var pageOne = catalog.findPublishedCatalogPage(tenant, 1, 1);
        var pageTwo = catalog.findPublishedCatalogPage(tenant, 2, 1);
        assertEquals(2L, pageOne.totalElements()); assertEquals(2L, pageTwo.totalElements());
        assertEquals(second.id(), pageOne.items().getFirst().sourceDeploymentId());
        assertEquals(first.id(), pageTwo.items().getFirst().sourceDeploymentId());
        assertTrue(catalog.findPublishedCatalogPage(tenant, 3, 1).items().isEmpty());
    }

    private PlatformModelDeployment verifiedPublication(String name, boolean currentTime) {
        var d = deployments.create(creation("publication-" + IDENTIFIERS.incrementAndGet(), "e".repeat(64), name)).deployment();
        if (currentTime) {
            VERIFICATION_NOW.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            jdbc.update("UPDATE model_runtime.platform_provider_connection SET last_probed_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(VERIFICATION_NOW.get()), connectionId.value());
            jdbc.update("UPDATE model_runtime.platform_provider_model_discovery SET discovered_at = ?, expires_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(VERIFICATION_NOW.get()), java.sql.Timestamp.from(VERIFICATION_NOW.get().plusSeconds(3600)), connectionId.value() + 1);
        }
        var r = reservation(d); verificationStore.reserve(r, () -> true);
        verificationStore.commit(r, success(d, r), "PASSED", 3, r.expectedSampleDigests(), () -> true);
        return deployments.findById(d.id()).orElseThrow();
    }

    private long count(String table) {
        if (!Set.of("controlled_model_catalog_entry", "platform_model_deployment_publication", "platform_model_supply_grant",
                "platform_model_supply_audit", "model_configuration").contains(table)) throw new IllegalArgumentException("合成表名不在范围");
        return jdbc.queryForObject("SELECT count(*) FROM model_runtime." + table, Long.class);
    }
    private static com.aiplatform.foundation.security.identity.PlatformRequestContext actor() {
        return new com.aiplatform.foundation.security.identity.PlatformRequestContext(new com.aiplatform.foundation.security.identity.SubjectId(101),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON, com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE, 1,
                Set.of(com.aiplatform.foundation.security.identity.PlatformPermissions.SESSION_ACCESS,
                    com.aiplatform.foundation.security.identity.PlatformPermissions.MODEL_PLATFORM_WRITE), "synthetic-publication");
    }
    private static java.time.Clock testClock() {
        return new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return VERIFICATION_NOW.get(); }
        };
    }
    private static com.aiplatform.foundation.security.identity.TenantFeatureAvailability featureFacts() {
        return (tenant, feature) -> {
            if (!FEATURES_AVAILABLE.get() || !ACTIVE_TENANTS.get().contains(tenant.value()) || !feature.equals("modelGovernance"))
                throw new IllegalStateException("合成模型功能不可用");
        };
    }
    private static com.aiplatform.foundation.security.identity.TenantActivityFacts tenantFacts() {
        return new com.aiplatform.foundation.security.identity.TenantActivityFacts() {
            public void requireActive(Set<Long> ids) {
                if (!ACTIVITY_FACTS_AVAILABLE.get() || !ACTIVE_TENANTS.get().containsAll(ids)) throw new IllegalArgumentException("合成租户未激活");
            }
            public boolean isActive(long id) { return ACTIVITY_FACTS_AVAILABLE.get() && ACTIVE_TENANTS.get().contains(id); }
        };
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @org.mybatis.spring.annotation.MapperScan(basePackageClasses = {
        com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionMapper.class,
        com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ControlledModelCatalogEntryMapper.class})
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
                        return tenantFacts();
                    },
                    testClock());
        }
        @Bean com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository catalog(
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ControlledModelCatalogEntryMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.ModelConfigurationMapper configurations,
                PlatformTransactionManager transactions) {
            return new com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.PostgresModelConfigurationRepository(
                    mapper, configurations, PostgresPlatformModelDeploymentRepositoryTest::tenantFacts, PostgresPlatformModelDeploymentRepositoryTest::featureFacts, transactions);
        }
        @Bean com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.publication.PlatformModelSupplyPublicationStore supply(
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformSupplyPublicationMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformCapabilityVerificationMapper verification,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentMapper locks,
                PlatformModelDeploymentRepository deployments, PlatformProviderConnectionRepository connections,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidates,
                com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository catalog,
                PlatformTransactionManager transactions) {
            return new PostgresPlatformModelSupplyPublicationStore(mapper, verification, locks, deployments, connections,
                    new PostgresPlatformProviderModelDiscoveryStore(transactions, connections, candidates), catalog,
                    () -> context -> { if (!ACTOR_CURRENT.get()) throw new com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException(); },
                    PostgresPlatformModelDeploymentRepositoryTest::tenantFacts, PostgresPlatformModelDeploymentRepositoryTest::featureFacts, IDENTIFIERS::incrementAndGet, testClock(), transactions);
        }
        @Bean PlatformModelDeploymentVerificationStore platformModelDeploymentVerificationStore(
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformCapabilityVerificationMapper mapper,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentMapper deploymentMapper,
                PlatformModelDeploymentRepository repository,
                com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidates,
                PlatformProviderConnectionRepository connections, PlatformTransactionManager transactions) {
            var clock = new java.time.Clock() {
                public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
                public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
                public Instant instant() { return VERIFICATION_NOW.get(); }
            };
            return new PostgresPlatformModelDeploymentVerificationStore(mapper, deploymentMapper, repository,
                    new PostgresPlatformProviderModelDiscoveryStore(transactions, connections, candidates),
                    () -> context -> { if (!ACTOR_CURRENT.get()) throw new com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException(); }, transactions, clock);
        }
    }
}
