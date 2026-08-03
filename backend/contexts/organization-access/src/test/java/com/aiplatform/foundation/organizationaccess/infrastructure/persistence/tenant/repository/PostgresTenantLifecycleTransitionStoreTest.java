package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantLifecycleTransitionCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleTransitionService;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleAction;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleTransitionMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 验证生命周期迁移在真实 PostgreSQL 中受锁、乐观更新并与发件箱一起提交。
 */
@SpringBootTest(
        classes = PostgresTenantLifecycleTransitionStoreTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresTenantLifecycleTransitionStoreTest {

    private static final long TENANT_ID = 100000000000000001L;
    private static final long INITIAL_ADMINISTRATOR = 4921825928971026434L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T04:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantLifecycleTransitionService transitions;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seedFacts() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_tenant_lifecycle_outbox ON organization_access.tenant_lifecycle_outbox");
        jdbc.execute("DROP FUNCTION IF EXISTS organization_access.fail_tenant_lifecycle_outbox()");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_authentication_cutover");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_outbox");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, 'initial-admin', 'valid-hash', 'ACTIVE', 0,
                    TIMESTAMPTZ '2026-07-01T00:00:00Z', TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, INITIAL_ADMINISTRATOR);
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'fresh-tenant', '新租户', 'DISABLED', 0,
                    TIMESTAMPTZ '2026-08-03T03:00:00Z', TIMESTAMPTZ '2026-08-03T03:00:00Z')
                """, TENANT_ID);
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle
                    (tenant_id, tenant_code, display_name, initial_administrator_subject_id, status, version, created_at)
                VALUES (?, 'fresh-tenant', '新租户', ?, 'PROVISIONING', 0,
                    TIMESTAMPTZ '2026-08-03T03:00:00Z')
                """, TENANT_ID, INITIAL_ADMINISTRATOR);
    }

    @Test
    void 激活必须只更新生命周期根并同一事务写入状态事实() {
        var result = transitions.transition(
                platformContext(),
                new TenantLifecycleTransitionCommand(new TenantId(TENANT_ID), TenantLifecycleAction.ACTIVATE));

        assertEquals("ACTIVE", result.lifecycle().status().name());
        assertEquals(1L, result.lifecycle().version());
        assertEquals("ACTIVE", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_lifecycle WHERE tenant_id = ?
                """, String.class, TENANT_ID));
        assertEquals("DISABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant WHERE tenant_id = ?
                """, String.class, TENANT_ID));
        assertEquals("ACTIVE", jdbc.queryForObject("""
                SELECT lifecycle_status FROM organization_access.tenant_lifecycle_outbox WHERE tenant_id = ?
                """, String.class, TENANT_ID));
    }

    @Test
    void 发件箱失败必须回滚生命周期乐观更新() {
        jdbc.execute("""
                CREATE FUNCTION organization_access.fail_tenant_lifecycle_outbox()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION '受控发件箱故障';
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_tenant_lifecycle_outbox
                BEFORE INSERT ON organization_access.tenant_lifecycle_outbox
                FOR EACH ROW
                EXECUTE FUNCTION organization_access.fail_tenant_lifecycle_outbox()
                """);

        assertThrows(RuntimeException.class, () -> transitions.transition(
                platformContext(),
                new TenantLifecycleTransitionCommand(new TenantId(TENANT_ID), TenantLifecycleAction.ACTIVATE)));

        assertEquals("PROVISIONING", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_lifecycle WHERE tenant_id = ?
                """, String.class, TENANT_ID));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM organization_access.tenant_lifecycle_outbox WHERE tenant_id = ?
                """, Long.class, TENANT_ID));
    }

    private static PlatformRequestContext platformContext() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_tenant_transition_store");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantLifecycleTransitionMapper.class)
    @Import({TenantLifecycleTransitionService.class, PostgresTenantLifecycleTransitionStore.class})
    static class TestApplication {

        @Bean
        EntityIdGenerator testIdentifiers() {
            var values = new AtomicLong(900000000000000000L);
            return values::incrementAndGet;
        }

        @Bean
        Clock testClock() {
            return Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
        }
    }
}
