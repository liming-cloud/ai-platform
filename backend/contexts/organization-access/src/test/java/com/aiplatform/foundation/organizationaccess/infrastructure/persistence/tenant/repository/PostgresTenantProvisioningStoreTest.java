package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
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
 * 验证新租户建立只以一个真实 PostgreSQL 事务写入最小生命周期事实。
 */
@SpringBootTest(
        classes = PostgresTenantProvisioningStoreTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresTenantProvisioningStoreTest {

    private static final long INITIAL_ADMINISTRATOR = 4921825928971026434L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T03:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantProvisioningService provisioning;

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
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, 'initial-admin', 'valid-hash', 'ACTIVE', 0,
                    TIMESTAMPTZ '2026-07-01T00:00:00Z', TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, INITIAL_ADMINISTRATOR);
    }

    @Test
    void 应原子建立不可认证兼容租户准备中生命周期和初始管理员() {
        var result = provisioning.provision(
                platformContext(),
                new TenantProvisioningCommand(
                        "fresh-tenant", "新租户", new SubjectId(INITIAL_ADMINISTRATOR)));

        assertEquals("DISABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant WHERE tenant_id = ?
                """, String.class, result.lifecycle().id().value()));
        assertEquals("PROVISIONING", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_lifecycle WHERE tenant_id = ?
                """, String.class, result.lifecycle().id().value()));
        assertEquals("TENANT_ADMINISTRATOR", jdbc.queryForObject("""
                SELECT membership_role FROM organization_access.tenant_membership WHERE membership_id = ?
                """, String.class, result.initialAdministrator().id().value()));
        assertEquals("PROVISIONING", jdbc.queryForObject("""
                SELECT lifecycle_status FROM organization_access.tenant_lifecycle_outbox WHERE tenant_id = ?
                """, String.class, result.lifecycle().id().value()));
    }

    @Test
    void 发件箱失败必须回滚兼容租户生命周期和成员资格() {
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

        assertThrows(RuntimeException.class, () -> provisioning.provision(
                platformContext(),
                new TenantProvisioningCommand(
                        "fresh-tenant", "新租户", new SubjectId(INITIAL_ADMINISTRATOR))));

        assertEquals(0L, count("organization_access.tenant"));
        assertEquals(0L, count("organization_access.tenant_lifecycle"));
        assertEquals(0L, count("organization_access.tenant_lifecycle_outbox"));
        assertEquals(0L, count("organization_access.tenant_membership"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static PlatformRequestContext platformContext() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_tenant_provisioning_store");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantProvisioningMapper.class)
    @Import({TenantProvisioningService.class, PostgresTenantProvisioningStore.class})
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
