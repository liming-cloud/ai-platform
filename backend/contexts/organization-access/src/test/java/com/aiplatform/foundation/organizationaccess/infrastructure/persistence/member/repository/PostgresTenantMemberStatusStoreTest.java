package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberStatusApplicationService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberStatusMapper;
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

/** 验证成员停用以真实 PostgreSQL 锁和所有有效权限来源保护最后访问管理员。 */
@SpringJUnitConfig(PostgresTenantMemberStatusStoreTest.TestApplication.class)
@Testcontainers
class PostgresTenantMemberStatusStoreTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId TARGET = new MemberId(3382738893456539650L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantMemberStatusApplicationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FailingSessionRevoker sessions;

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
                VALUES (?, 'member-status', '成员状态测试租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT.value(), NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (4921825928971026434, 'member-status-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz)
                """, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (4921825928971026435, 'member-status-backup-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz)
                """, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version, created_at, updated_at)
                VALUES (?, ?, 4921825928971026434, 'MEMBER', 'ENABLED', 3, 7, ?::timestamptz, ?::timestamptz)
                """, TARGET.value(), TENANT.value(), NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding
                    (tenant_id, membership_id, role_code, bound_at)
                VALUES (?, ?, 'TENANT_ACCESS_ADMINISTRATOR', ?::timestamptz)
                """, TENANT.value(), TARGET.value(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version, created_at, updated_at)
                VALUES (3382738893456539651, ?, 4921825928971026435, 'MEMBER', 'ENABLED', 1, 0, ?::timestamptz, ?::timestamptz)
                """, TENANT.value(), NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding
                    (tenant_id, membership_id, role_code, bound_at)
                VALUES (?, 3382738893456539651, 'TENANT_ACCESS_ADMINISTRATOR', ?::timestamptz)
                """, TENANT.value(), NOW.toString());
    }

    @Test
    void 会话撤销失败时成员状态变更必须回滚() {
        sessions.fail();

        assertThrows(IllegalStateException.class,
                () -> service.disable(manager(), TARGET.value()));

        assertEquals("ENABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT.value(), TARGET.value()));
        assertEquals(3L, jdbc.queryForObject("""
                SELECT permission_version FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, Long.class, TENANT.value(), TARGET.value()));
    }

    @Test
    void 停用成员必须原子更新状态和权限修订() {
        service.disable(manager(), TARGET.value());

        assertEquals("DISABLED", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT.value(), TARGET.value()));
        assertEquals(4L, jdbc.queryForObject("""
                SELECT permission_version FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND membership_id = ?
                """, Long.class, TENANT.value(), TARGET.value()));
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539649L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                Set.of(new PermissionCode("access:manage")),
                "trc_postgres_member_status");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = TenantMemberStatusMapper.class)
    @Import({TenantMemberStatusApplicationService.class, PostgresTenantMemberStatusStore.class})
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

        @Override
        public void revokeTenantSessions(MemberId memberId) {
            if (failing.get()) {
                throw new IllegalStateException("模拟会话撤销失败");
            }
        }

        void fail() {
            failing.set(true);
        }

        void reset() {
            failing.set(false);
        }
    }
}
