package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorCredentialService;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository.PostgresPlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证平台租户管理员生命周期在真实 PostgreSQL 中保持角色、事务与会话撤销一致。 */
@SpringJUnitConfig(PostgresPlatformTenantAdministratorStoreTest.TestApplication.class)
@Testcontainers
class PostgresPlatformTenantAdministratorStoreTest {

    private static final long TENANT = 100000000000000001L;
    private static final long OLD_MEMBERSHIP = 7001L;
    private static final long OLD_SUBJECT = 7101L;
    private static final long CANDIDATE_MEMBERSHIP = 7002L;
    private static final long CANDIDATE_SUBJECT = 7102L;
    private static final long FRESH_SUBJECT = 7103L;
    private static final Instant NOW = Instant.parse("2026-08-31T04:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PlatformTenantAdministratorApplicationService service;

    @Autowired
    private PlatformTenantAdministratorCredentialService credentials;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FailingSessionFamilyRevoker sessions;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seedFacts() {
        sessions.reset();
        jdbc.update("DELETE FROM organization_access.platform_role_binding");
        jdbc.update("DELETE FROM organization_access.platform_subject_authorization");
        jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding");
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_outbox");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'platform-admin-governance', '管理员治理租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES
                    (?, 'previous-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz),
                    (?, 'candidate-user', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz),
                    (?, 'fresh-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz)
                """,
                OLD_SUBJECT, NOW.toString(), NOW.toString(),
                CANDIDATE_SUBJECT, NOW.toString(), NOW.toString(),
                FRESH_SUBJECT, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle
                    (tenant_id, tenant_code, display_name, initial_administrator_subject_id,
                     status, version, created_at)
                VALUES (?, 'platform-admin-governance', '管理员治理租户', ?, 'ACTIVE', 0,
                    ?::timestamptz)
                """, TENANT, OLD_SUBJECT, NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status,
                     permission_version, version, created_at, updated_at)
                VALUES
                    (?, ?, ?, 'TENANT_ADMINISTRATOR', 'ENABLED', 2, 5, ?::timestamptz, ?::timestamptz),
                    (?, ?, ?, 'TENANT_USER', 'ENABLED', 1, 3, ?::timestamptz, ?::timestamptz)
                """,
                OLD_MEMBERSHIP, TENANT, OLD_SUBJECT, NOW.toString(), NOW.toString(),
                CANDIDATE_MEMBERSHIP, TENANT, CANDIDATE_SUBJECT, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding
                    (tenant_id, membership_id, role_code, bound_at)
                VALUES
                    (?, ?, 'TENANT_ADMINISTRATOR', ?::timestamptz),
                    (?, ?, 'TENANT_USER', ?::timestamptz)
                """,
                TENANT, OLD_MEMBERSHIP, NOW.toString(),
                TENANT, CANDIDATE_MEMBERSHIP, NOW.toString());
    }

    @Test
    void 分配既有普通成员必须同步主角色和唯一管理员绑定() {
        var view = service.assign(platformAdministrator(), Long.toString(TENANT), CANDIDATE_SUBJECT);

        assertEquals("TENANT_ADMINISTRATOR", view.role());
        assertEquals("TENANT_ADMINISTRATOR", jdbc.queryForObject("""
                SELECT membership_role FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT, CANDIDATE_MEMBERSHIP));
        assertEquals(java.util.List.of("TENANT_ADMINISTRATOR"), jdbc.queryForList("""
                SELECT role_code FROM organization_access.tenant_membership_role_binding
                WHERE tenant_id = ? AND membership_id = ? ORDER BY role_code
                """, String.class, TENANT, CANDIDATE_MEMBERSHIP));
    }

    @Test
    void 替换时会话撤销失败必须回滚新管理员与旧管理员状态() {
        sessions.fail();

        assertThrows(IllegalStateException.class, () -> service.replace(
                platformAdministrator(), Long.toString(TENANT), OLD_MEMBERSHIP, 5L, FRESH_SUBJECT));

        assertEquals("ENABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT, OLD_MEMBERSHIP));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND subject_id = ?
                """, Long.class, TENANT, FRESH_SUBJECT));
    }

    @Test
    void 租户管理员密码重置的会话撤销失败必须回滚全局身份() {
        sessions.fail();

        assertThrows(IllegalStateException.class, () -> credentials.resetPassword(
                platformAdministrator(), Long.toString(TENANT), OLD_MEMBERSHIP, 5L,
                new char[]{'a', 'b', 'c'}));

        assertEquals("valid-hash", jdbc.queryForObject("""
                SELECT password_hash FROM organization_access.subject_identity WHERE subject_id = ?
                """, String.class, OLD_SUBJECT));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT version FROM organization_access.subject_identity WHERE subject_id = ?
                """, Long.class, OLD_SUBJECT));
    }

    @Test
    void 租户管理员密码重置只更新对应全局身份且保留成员资格() {
        credentials.resetPassword(platformAdministrator(), Long.toString(TENANT), OLD_MEMBERSHIP, 5L,
                new char[]{'a', 'b', 'c'});

        assertEquals("test-replacement-digest", jdbc.queryForObject("""
                SELECT password_hash FROM organization_access.subject_identity WHERE subject_id = ?
                """, String.class, OLD_SUBJECT));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT version FROM organization_access.subject_identity WHERE subject_id = ?
                """, Long.class, OLD_SUBJECT));
        assertTrue(jdbc.queryForObject("""
                SELECT updated_at FROM organization_access.subject_identity WHERE subject_id = ?
                """, java.sql.Timestamp.class, OLD_SUBJECT).toInstant().isAfter(NOW));
        assertEquals("ENABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT, OLD_MEMBERSHIP));
    }

    @Test
    void 只有一个启用管理员时不得停用() {
        assertThrows(OrganizationAccessRuleViolation.class, () -> service.disable(
                platformAdministrator(), Long.toString(TENANT), OLD_MEMBERSHIP, 5L));

        assertEquals("ENABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT, OLD_MEMBERSHIP));
    }

    @Test
    void 已任其他租户管理员的主体不得再分配且数据库拒绝并发绕过() {
        var anotherTenant = TENANT + 1L;
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'second-admin-tenant', '第二租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, anotherTenant, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status,
                     permission_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 'TENANT_ADMINISTRATOR', 'DISABLED', 0, 0,
                    ?::timestamptz, ?::timestamptz)
                """, OLD_MEMBERSHIP + 10L, anotherTenant, CANDIDATE_SUBJECT, NOW.toString(), NOW.toString());

        assertThrows(OrganizationAccessRuleViolation.class, () -> service.assign(
                platformAdministrator(), Long.toString(TENANT), CANDIDATE_SUBJECT));
        assertEquals("TENANT_USER", jdbc.queryForObject("""
                SELECT membership_role FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND subject_id = ?
                """, String.class, TENANT, CANDIDATE_SUBJECT));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status,
                     permission_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 'TENANT_ADMINISTRATOR', 'ENABLED', 0, 0,
                    ?::timestamptz, ?::timestamptz)
                """, OLD_MEMBERSHIP + 11L, anotherTenant, OLD_SUBJECT, NOW.toString(), NOW.toString()));
    }

    @Test
    void 并发停用两位管理员时必须以租户共同锁保留一位() throws Exception {
        service.assign(platformAdministrator(), Long.toString(TENANT), CANDIDATE_SUBJECT);
        var candidateVersion = jdbc.queryForObject("""
                SELECT version FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, Long.class, TENANT, CANDIDATE_MEMBERSHIP);
        sessions.block(new SubjectId(OLD_SUBJECT));
        var executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> service.disable(
                platformAdministrator(), Long.toString(TENANT), OLD_MEMBERSHIP, 5L));
        assertTrue(sessions.awaitBlocked());
        var second = executor.submit(() -> {
            try {
                service.disable(
                        platformAdministrator(), Long.toString(TENANT),
                        CANDIDATE_MEMBERSHIP, candidateVersion);
                return "DISABLED";
            } catch (OrganizationAccessRuleViolation exception) {
                return "REJECTED";
            }
        });
        try {
            assertThrows(TimeoutException.class, () -> second.get(500L, TimeUnit.MILLISECONDS));
        } finally {
            sessions.release();
        }
        first.get(5L, TimeUnit.SECONDS);
        assertEquals("REJECTED", second.get(5L, TimeUnit.SECONDS));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM organization_access.tenant_membership
                WHERE tenant_id = ?
                  AND membership_role = 'TENANT_ADMINISTRATOR'
                  AND status = 'ENABLED'
                """, Long.class, TENANT));
        executor.shutdownNow();
    }

    private static PlatformRequestContext platformAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(1L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(
                        PlatformPermissions.SESSION_ACCESS,
                        PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_postgres_platform_tenant_administrator");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {PlatformTenantAdministratorMapper.class, PlatformAccountMapper.class})
    @Import({
            PlatformTenantAdministratorApplicationService.class,
            PlatformTenantAdministratorCredentialService.class,
            PostgresPlatformTenantAdministratorStore.class,
            PostgresPlatformAccountStore.class
    })
    static class TestApplication {

        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator identifiers() {
            return () -> 9001L;
        }

        @Bean
        FailingSessionFamilyRevoker sessionRevoker() {
            return new FailingSessionFamilyRevoker();
        }

        @Bean
        PasswordHasher testPasswordHasher() {
            return password -> "test-replacement-digest";
        }
    }

    static final class FailingSessionFamilyRevoker implements PersonSessionFamilyRevoker {

        private final AtomicBoolean failing = new AtomicBoolean();
        private volatile SubjectId blockedSubject;
        private volatile CountDownLatch blocked = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        @Override
        public void revokeAll(SubjectId subjectId) {
            if (failing.get()) {
                throw new IllegalStateException("模拟会话撤销失败");
            }
            if (subjectId.equals(blockedSubject)) {
                blocked.countDown();
                try {
                    if (!release.await(5L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待并发管理员操作超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待并发管理员操作被中断", exception);
                }
            }
        }

        void fail() {
            failing.set(true);
        }

        void reset() {
            failing.set(false);
            blockedSubject = null;
            blocked = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        void block(SubjectId subjectId) {
            blockedSubject = subjectId;
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        boolean awaitBlocked() throws InterruptedException {
            return blocked.await(5L, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }
    }
}
