package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantRoleRevocationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleRevocationApplicationService;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
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

/** 验证内置角色撤销、权限修订和会话失效在真实 PostgreSQL 事务中一致完成。 */
@SpringJUnitConfig(PostgresTenantRoleRevocationStoreTest.TestApplication.class)
@Testcontainers
class PostgresTenantRoleRevocationStoreTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId TARGET = new MemberId(3382738893456539652L);
    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantRoleRevocationApplicationService service;

    @Autowired
    private FailingSessionRevoker sessions;

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
        sessions.reset();
        jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding");
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'role-revocation', '角色撤销测试租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT.value(), NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (4921825928971026436, 'role-revocation-target', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz)
                """, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version, created_at, updated_at)
                VALUES (?, ?, 4921825928971026436, 'TENANT_USER', 'ENABLED', 4, 9, ?::timestamptz, ?::timestamptz)
                """, TARGET.value(), TENANT.value(), NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding
                    (tenant_id, membership_id, role_code, bound_at)
                VALUES (?, ?, 'TENANT_ADMINISTRATOR', ?::timestamptz)
                """, TENANT.value(), TARGET.value(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.membership_permission
                    (tenant_id, membership_id, permission_code)
                VALUES (?, ?, 'access:manage')
                """, TENANT.value(), TARGET.value());
    }

    @Test
    void 应删除角色绑定并推进成员权限版本() {
        service.revoke(manager(), command());

        assertEquals(0L, count("organization_access.tenant_membership_role_binding"));
        assertEquals(5L, jdbc.queryForObject("""
                SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = ?
                """, Long.class, TARGET.value()));
        assertEquals(10L, jdbc.queryForObject("""
                SELECT version FROM organization_access.tenant_membership WHERE membership_id = ?
                """, Long.class, TARGET.value()));
        assertEquals(TARGET, sessions.revokedMember());
    }

    @Test
    void 会话撤销失败必须回滚角色删除和权限版本修订() {
        sessions.fail();

        assertThrows(IllegalStateException.class, () -> service.revoke(manager(), command()));

        assertEquals(1L, count("organization_access.tenant_membership_role_binding"));
        assertEquals(4L, jdbc.queryForObject("""
                SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = ?
                """, Long.class, TARGET.value()));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static TenantRoleRevocationCommand command() {
        return new TenantRoleRevocationCommand(
                TARGET.value(), TenantBuiltInRole.TENANT_ADMINISTRATOR.name());
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                Set.of(new PermissionCode("access:manage")),
                "trc_postgres_role_revocation");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = TenantRoleBindingMapper.class)
    @Import({TenantRoleRevocationApplicationService.class, PostgresTenantRoleRevocationStore.class})
    static class TestApplication {

        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        FailingSessionRevoker sessionRevoker() {
            return new FailingSessionRevoker();
        }
    }

    static final class FailingSessionRevoker implements TenantMemberSessionRevoker {

        private final AtomicBoolean failing = new AtomicBoolean();
        private MemberId revokedMember;

        @Override
        public void revokeTenantSessions(MemberId membershipId) {
            if (failing.get()) {
                throw new IllegalStateException("会话撤销失败");
            }
            revokedMember = membershipId;
        }

        void fail() {
            failing.set(true);
        }

        void reset() {
            failing.set(false);
            revokedMember = null;
        }

        MemberId revokedMember() {
            return revokedMember;
        }
    }
}
