package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.password.Pbkdf2PasswordAdapter;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
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

    @Autowired private TenantMembershipMapper memberships;
    @Autowired private PostgresTenantLifecycleReadAdapter readAdapter;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seedFacts() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_identity_write ON organization_access.subject_identity");
        jdbc.execute("DROP FUNCTION IF EXISTS organization_access.fail_identity_write()");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_tenant_lifecycle_outbox ON organization_access.tenant_lifecycle_outbox");
        jdbc.execute("DROP FUNCTION IF EXISTS organization_access.fail_tenant_lifecycle_outbox()");
        jdbc.update("DELETE FROM organization_access.platform_role_binding");
        jdbc.update("DELETE FROM organization_access.platform_subject_authorization");
        jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_authentication_cutover");
        jdbc.update("INSERT INTO organization_access.tenant_lifecycle_authentication_cutover VALUES (TRUE, CURRENT_TIMESTAMP)");
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
    void 应原子建立可登录租户新身份角色非默认配额和事件() {
        var result = provisioning.provision(
                platformContext(), command("fresh-tenant", "fresh.admin"), password());

        assertEquals("DISABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant WHERE tenant_id = ?
                """, String.class, result.tenantId()));
        assertEquals("ACTIVE", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_lifecycle WHERE tenant_id = ?
                """, String.class, result.tenantId()));
        assertEquals("TENANT_ADMINISTRATOR", jdbc.queryForObject("""
                SELECT membership_role FROM organization_access.tenant_membership WHERE tenant_id = ?
                """, String.class, result.tenantId()));
        assertEquals("TENANT_ADMINISTRATOR", jdbc.queryForObject("""
                SELECT role_code FROM organization_access.tenant_membership_role_binding WHERE tenant_id = ?
                """, String.class, result.tenantId()));
        assertEquals("ACTIVE", jdbc.queryForObject("""
                SELECT lifecycle_status FROM organization_access.tenant_lifecycle_outbox WHERE tenant_id = ?
                """, String.class, result.tenantId()));
        assertEquals(4, jdbc.queryForObject("SELECT knowledge_bases FROM organization_access.tenant_quota WHERE tenant_id=?", Integer.class, result.tenantId()));
        assertEquals(1L, result.version());
        assertNotNull(memberships.findEnabled(result.tenantCode(), result.initialAdministratorSubjectId()));
        var hash = jdbc.queryForObject("SELECT password_hash FROM organization_access.subject_identity WHERE subject_id=?", String.class, result.initialAdministratorSubjectId());
        assertTrue(new Pbkdf2PasswordAdapter(new java.security.SecureRandom()).matches(password(), hash));
        assertEquals(0L, count("organization_access.platform_role_binding"));
        assertEquals(result, readAdapter.find(result.tenantId()).orElseThrow());
        var savedQuota = readAdapter.quotas(result.tenantId()).orElseThrow();
        assertEquals(4, savedQuota.knowledgeBases());
        assertEquals(2000, savedQuota.documentsPerKnowledgeBase());
        assertEquals("20000000000", savedQuota.bytesPerKnowledgeBase());
        assertEquals("60000000", savedQuota.maxFileBytes());
        assertEquals("2097152", savedQuota.maxTextBytes());
        assertEquals(0L, savedQuota.version());
    }

    @Test
    void HTTP所用入口在发件箱失败后必须整体回滚() {
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
                platformContext(), command("fresh-tenant", "fresh.admin"), password()));

        assertEquals(0L, count("organization_access.tenant"));
        assertEquals(0L, count("organization_access.tenant_lifecycle"));
        assertEquals(0L, count("organization_access.tenant_lifecycle_outbox"));
        assertEquals(0L, count("organization_access.tenant_membership_role_binding"));
        assertEquals(0L, count("organization_access.tenant_membership"));
        assertEquals(0L, count("organization_access.tenant_quota"));
        assertEquals(1L, count("organization_access.subject_identity"));
    }

    @Test
    void 已有平台管理员登录名不得复用且不得留下租户() {
        jdbc.update("""
                INSERT INTO organization_access.platform_role_binding
                    (binding_id, subject_id, role_code, status, version, created_at)
                VALUES (4921825928971026437, ?, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0,
                    TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, INITIAL_ADMINISTRATOR);

        assertThrows(ApplicationException.class, () -> provisioning.provision(
                platformContext(), command("fresh-tenant", "initial-admin"), password()));

        assertEquals(0L, count("organization_access.tenant"));
    }

    @Test
    void 认证环境未就绪必须无任何写入() {
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_authentication_cutover");
        assertThrows(ApplicationException.class, () -> provisioning.provision(
                platformContext(), command("fresh-tenant", "fresh.admin"), password()));
        assertEquals(0L, count("organization_access.tenant"));
        assertEquals(1L, count("organization_access.subject_identity"));
    }

    @Test
    void 身份存储异常含摘要时必须替换为无原因链安全错误() {
        jdbc.execute("""
                CREATE FUNCTION organization_access.fail_identity_write() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION '受控故障 %', NEW.password_hash; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_identity_write BEFORE INSERT ON organization_access.subject_identity
                FOR EACH ROW EXECUTE FUNCTION organization_access.fail_identity_write()
                """);
        var failure = assertThrows(RuntimeException.class, () -> provisioning.provision(
                platformContext(), command("fresh-tenant", "fresh.admin"), password()));
        assertTrue(failure instanceof ApplicationException);
        org.junit.jupiter.api.Assertions.assertNull(failure.getCause());
        assertEquals(0L, count("organization_access.tenant"));
        assertEquals(1L, count("organization_access.subject_identity"));
    }

    @Test
    void 重复租户编码必须回滚已写入的新身份() {
        provisioning.provision(platformContext(), command("fresh-tenant", "fresh.admin"), password());
        assertThrows(ApplicationException.class, () -> provisioning.provision(
                platformContext(), command("fresh-tenant", "other.admin"), password()));
        assertEquals(1L, count("organization_access.tenant"));
        assertEquals(2L, count("organization_access.subject_identity"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void 并发同登录名或同编码只能一个完整成功且无孤儿(boolean sameLogin) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发测试协调超时");
                }
                try {
                    provisioning.provision(platformContext(), command(
                            sameLogin ? "concurrent-" + index : "concurrent-one",
                            sameLogin ? "concurrent.admin" : "concurrent.admin" + index), password());
                    return true;
                } catch (ApplicationException conflict) {
                    assertTrue(conflict.getMessage().contains("已被使用"));
                    return false;
                }
            })).toList();
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var successes = 0;
            for (var attempt : attempts) {
                if (attempt.get(15, java.util.concurrent.TimeUnit.SECONDS)) { successes++; }
            }
            assertEquals(1, successes);
        }
        assertEquals(2L, count("organization_access.subject_identity"));
        for (var table : java.util.List.of("tenant", "tenant_lifecycle", "tenant_membership",
                "tenant_membership_role_binding", "tenant_quota", "tenant_lifecycle_outbox")) {
            assertEquals(1L, count("organization_access." + table), table);
        }
        assertEquals(0L, count("organization_access.platform_role_binding"));
    }

    private static TenantProvisioningCommand command(String code, String login) {
        return new TenantProvisioningCommand(code, "新租户", login, 4, 2000, 20000000000L, 60000000L, 2097152L);
    }

    private static char[] password() { return "开户测试密码@2026".toCharArray(); }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static PlatformRequestContext platformContext() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_tenant_provisioning_store");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = {TenantProvisioningMapper.class, SubjectIdentityMapper.class, TenantMembershipMapper.class})
    @Import({TenantProvisioningService.class, PostgresTenantProvisioningStore.class, PostgresTenantLifecycleReadAdapter.class})
    static class TestApplication {

        @Bean PasswordHasher passwordHasher() { return new Pbkdf2PasswordAdapter(new java.security.SecureRandom()); }

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
