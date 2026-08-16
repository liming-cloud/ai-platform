package com.aiplatform.foundation.modelruntime.infrastructure.persistence.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.modelruntime.application.operations.facade.ModelOperationsFactSource;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = JdbcModelOperationsFactSourceTest.TestApplication.class)
@Testcontainers
class JdbcModelOperationsFactSourceTest {

    private static final TenantId HEALTHY_TENANT = new TenantId(7L);
    private static final TenantId PENDING_TENANT = new TenantId(8L);
    private static final TenantId EXPIRED_TENANT = new TenantId(9L);
    private static final TenantId CONFIGURED_TENANT = new TenantId(10L);

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private ModelOperationsFactSource facts;

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
    void seedFacts() {
        jdbc.update("TRUNCATE TABLE model_runtime.model_configuration CASCADE");
        jdbc.update("TRUNCATE TABLE model_runtime.controlled_model_catalog_entry CASCADE");
        jdbc.update("TRUNCATE TABLE model_runtime.platform_provider_connection CASCADE");
        jdbc.update("TRUNCATE TABLE model_runtime.provider_connection CASCADE");

        platformConnection(101L, "待验证连接", "DRAFT", null);
        platformConnection(102L, "失败连接", "DRAFT", false);
        platformConnection(103L, "可用连接", "VERIFIED", true);
        platformDeployment(201L, 103L, "可用部署", true);
        platformDeployment(202L, 103L, "过期部署", false);
        platformDraftDeployment(203L, 103L, "待探测部署");
        usableTenantConfiguration(CONFIGURED_TENANT.value(), 201L);

        tenantConnection(HEALTHY_TENANT.value(), 301L, "待验证连接", "DRAFT", null);
        tenantConnection(HEALTHY_TENANT.value(), 302L, "失败连接", "DRAFT", false);
        tenantConnection(HEALTHY_TENANT.value(), 303L, "可用连接", "VERIFIED", true);
        tenantDeployment(HEALTHY_TENANT.value(), 401L, 303L, "可用部署", true);
        tenantDeployment(HEALTHY_TENANT.value(), 402L, 303L, "过期部署", false);
        tenantConnection(PENDING_TENANT.value(), 304L, "仅待验证连接", "DRAFT", null);
        tenantConnection(EXPIRED_TENANT.value(), 305L, "过期租户连接", "VERIFIED", true);
        tenantDeployment(EXPIRED_TENANT.value(), 403L, 305L, "租户过期部署", false);
    }

    @Test
    void 平台运营必须按真实探测结果与当前可用性分类() {
        var summary = facts.platformSummary();

        assertEquals(Map.of("FAILED", 1L, "PENDING", 1L, "VERIFIED", 1L),
                summary.platformConnectionsByStatus());
        assertEquals(Map.of("PUBLISHED", 1L, "UNAVAILABLE", 2L),
                summary.platformDeploymentsByStatus());
    }

    @Test
    void 租户运营必须隔离当前租户并识别没有可用部署的已配置租户() {
        var summary = facts.tenantSummary(HEALTHY_TENANT);

        assertEquals(Map.of("FAILED", 1L, "PENDING", 1L, "VERIFIED", 1L),
                summary.tenantConnectionsByStatus());
        assertEquals(Map.of("PUBLISHED", 1L, "UNAVAILABLE", 1L),
                summary.tenantDeploymentsByStatus());
        assertEquals(Set.of(PENDING_TENANT, EXPIRED_TENANT),
                facts.tenantsWithoutPublishedDeployment());
    }

    private void platformConnection(long id, String name, String status, Boolean probeSucceeded) {
        jdbc.update("""
                INSERT INTO model_runtime.platform_provider_connection (
                    id, scope, provider_kind, display_name, endpoint, region, authentication_type,
                    credential_reference, status, version, last_probe_succeeded, last_probe_code,
                    last_probed_at, created_at)
                VALUES (?, 'PLATFORM', 'DETERMINISTIC', ?, 'memory://deterministic', 'local', 'API_KEY',
                    ?, ?, 0, ?, CASE WHEN CAST(? AS BOOLEAN) IS NULL THEN NULL ELSE 'PROBE_RESULT' END,
                    CASE WHEN CAST(? AS BOOLEAN) IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END, CURRENT_TIMESTAMP)
                """, id, name, "secret://platform-provider-connection/" + id, status,
                probeSucceeded, probeSucceeded, probeSucceeded);
    }

    private void platformDraftDeployment(long id, long connectionId, String name) {
        jdbc.update("""
                INSERT INTO model_runtime.platform_model_deployment (
                    id, scope, platform_provider_connection_id, platform_provider_connection_revision,
                    model_code, display_name, model_type, capabilities, context_window, output_limit,
                    embedding_dimension, status, version, created_at)
                VALUES (?, 'PLATFORM', ?, 0, 'deterministic-chat', ?, 'CHAT', ARRAY['CHAT'],
                    8192, 1024, NULL, 'DRAFT', 0, CURRENT_TIMESTAMP)
                """, id, connectionId, name);
    }

    private void platformDeployment(long id, long connectionId, String name, boolean current) {
        platformDraftDeployment(id, connectionId, name);
        var verificationId = id + 1000L;
        jdbc.update("""
                INSERT INTO model_runtime.platform_model_capability_verification (
                    id, platform_model_deployment_id, deployment_revision,
                    platform_provider_connection_id, platform_provider_connection_revision, model_code,
                    declared_capabilities, observed_capabilities, succeeded, result_code, elapsed_millis,
                    probed_at, expires_at)
                VALUES (?, ?, 0, ?, 0, 'deterministic-chat', ARRAY['CHAT'], ARRAY['CHAT'], TRUE,
                    'PROBE_OK', 1, CURRENT_TIMESTAMP - INTERVAL '2 days',
                    CURRENT_TIMESTAMP + CASE WHEN ? THEN INTERVAL '1 day' ELSE INTERVAL '-1 day' END)
                """, verificationId, id, connectionId, current);
        jdbc.update("""
                UPDATE model_runtime.platform_model_deployment
                SET status = 'PUBLISHED', last_verification_id = ?,
                    verification_expires_at = CURRENT_TIMESTAMP
                        + CASE WHEN ? THEN INTERVAL '1 day' ELSE INTERVAL '-1 day' END,
                    version = 1
                WHERE id = ?
                """, verificationId, current, id);
    }

    private void tenantConnection(long tenantId, long id, String name, String status, Boolean probeSucceeded) {
        jdbc.update("""
                INSERT INTO model_runtime.provider_connection (
                    tenant_id, id, provider_kind, display_name, endpoint, region, authentication_type,
                    credential_reference, status, version, last_probe_succeeded, last_probe_code,
                    last_probed_at, created_at)
                VALUES (?, ?, 'DETERMINISTIC', ?, 'memory://deterministic', 'local', 'API_KEY',
                    'secret://tenant-provider', ?, 0, ?,
                    CASE WHEN CAST(? AS BOOLEAN) IS NULL THEN NULL ELSE 'PROBE_RESULT' END,
                    CASE WHEN CAST(? AS BOOLEAN) IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END, CURRENT_TIMESTAMP)
                """, tenantId, id, name, status, probeSucceeded, probeSucceeded, probeSucceeded);
    }

    private void tenantDraftDeployment(long tenantId, long id, long connectionId, String name) {
        jdbc.update("""
                INSERT INTO model_runtime.model_deployment (
                    tenant_id, id, provider_connection_id, provider_connection_revision, model_code,
                    display_name, model_type, capabilities, context_window, output_limit,
                    embedding_dimension, status, version, created_at)
                VALUES (?, ?, ?, 0, 'deterministic-chat', ?, 'CHAT', ARRAY['CHAT'],
                    8192, 1024, NULL, 'DRAFT', 0, CURRENT_TIMESTAMP)
                """, tenantId, id, connectionId, name);
    }

    private void tenantDeployment(long tenantId, long id, long connectionId, String name, boolean current) {
        tenantDraftDeployment(tenantId, id, connectionId, name);
        var verificationId = id + 2000L;
        jdbc.update("""
                INSERT INTO model_runtime.model_capability_verification (
                    id, tenant_id, model_deployment_id, model_deployment_version,
                    provider_connection_id, provider_connection_revision, model_code,
                    declared_capabilities, observed_capabilities, succeeded, result_code,
                    elapsed_millis, probed_at, expires_at)
                VALUES (?, ?, ?, 0, ?, 0, 'deterministic-chat', ARRAY['CHAT'], ARRAY['CHAT'], TRUE,
                    'PROBE_OK', 1, CURRENT_TIMESTAMP - INTERVAL '2 days',
                    CURRENT_TIMESTAMP + CASE WHEN ? THEN INTERVAL '1 day' ELSE INTERVAL '-1 day' END)
                """, verificationId, tenantId, id, connectionId, current);
        jdbc.update("""
                UPDATE model_runtime.model_deployment
                SET status = 'PUBLISHED', last_verification_id = ?,
                    verification_expires_at = CURRENT_TIMESTAMP
                        + CASE WHEN ? THEN INTERVAL '1 day' ELSE INTERVAL '-1 day' END,
                    version = 1
                WHERE tenant_id = ? AND id = ?
                """, verificationId, current, tenantId, id);
    }

    private void usableTenantConfiguration(long tenantId, long sourceDeploymentId) {
        jdbc.update("""
                INSERT INTO model_runtime.controlled_model_catalog_entry (
                    id, provider_kind, endpoint, model_code, model_type, context_window, output_limit,
                    embedding_dimension, status, version, created_at, source_platform_deployment_id,
                    source_platform_deployment_revision)
                VALUES (501, 'DETERMINISTIC', 'memory://deterministic', 'deterministic-chat', 'CHAT',
                    8192, 1024, NULL, 'PUBLISHED', 1, CURRENT_TIMESTAMP, ?, 1)
                """, sourceDeploymentId);
        jdbc.update("""
                INSERT INTO model_runtime.model_configuration (
                    id, scope, tenant_id, owner_membership_id, display_name, catalog_entry_id,
                    status, version, created_at)
                VALUES (601, 'TENANT', ?, NULL, '租户可用配置', 501, 'ACTIVE', 1, CURRENT_TIMESTAMP)
                """, tenantId);
        jdbc.update("""
                INSERT INTO model_runtime.model_configuration_credential (
                    model_configuration_id, algorithm, master_key_version, data_key_nonce,
                    encrypted_data_key, ciphertext_nonce, ciphertext, fingerprint, rotated_at)
                VALUES (601, 'AES-256-GCM', 'local', decode(repeat('01', 12), 'hex'),
                    decode('01', 'hex'), decode(repeat('02', 12), 'hex'), decode('02', 'hex'),
                    repeat('a', 64), CURRENT_TIMESTAMP)
                """);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        ModelOperationsFactSource modelOperationsFactSource(JdbcClient jdbc) {
            return new JdbcModelOperationsFactSource(jdbc);
        }
    }
}
