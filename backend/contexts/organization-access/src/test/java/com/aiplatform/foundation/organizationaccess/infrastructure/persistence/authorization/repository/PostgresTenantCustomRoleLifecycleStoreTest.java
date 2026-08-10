package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRolePermissionChangeCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleLifecycleApplicationService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleLifecycleMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

/** 验证自定义角色变更、成员修订与会话撤销在真实 PostgreSQL 事务中一致完成。 */
@SpringJUnitConfig(PostgresTenantCustomRoleLifecycleStoreTest.TestApplication.class)
@Testcontainers
class PostgresTenantCustomRoleLifecycleStoreTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24")
            .withDatabaseName("foundation_test")
            .withUsername("foundation")
            .withPassword("foundation_test_only");

    @Autowired
    private TenantCustomRoleLifecycleApplicationService service;

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
    void seed() {
        sessions.reset();
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_binding");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_permission");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status, version) VALUES (?, 'custom-lifecycle', '自定义角色生命周期测试', 'ENABLED', 0)", TENANT.value());
        jdbc.update("INSERT INTO organization_access.subject_identity (subject_id, login_name, password_hash, status, version) VALUES (4921825928971026434, 'lifecycle-admin', 'valid-hash', 'ACTIVE', 0), (4921825928971026435, 'lifecycle-member', 'valid-hash', 'ACTIVE', 0)");
        jdbc.update("INSERT INTO organization_access.tenant_membership (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version) VALUES (9001, ?, 4921825928971026434, 'MEMBER', 'ENABLED', 2, 3), (9002, ?, 4921825928971026435, 'MEMBER', 'ENABLED', 4, 5)", TENANT.value(), TENANT.value());
        jdbc.update("INSERT INTO organization_access.tenant_custom_role (custom_role_id, tenant_id, role_name, status, version, created_at) VALUES (8001, ?, '模型观察员', 'ACTIVE', 0, ?::timestamptz)", TENANT.value(), NOW.toString());
        jdbc.update("INSERT INTO organization_access.tenant_custom_role_permission (custom_role_id, permission_code) VALUES (8001, 'model:read')");
        jdbc.update("INSERT INTO organization_access.tenant_custom_role_binding (tenant_id, membership_id, custom_role_id, bound_at) VALUES (?, 9001, 8001, ?::timestamptz), (?, 9002, 8001, ?::timestamptz)", TENANT.value(), NOW.toString(), TENANT.value(), NOW.toString());
    }

    @Test
    void 应原子替换角色权限并推进全部绑定成员的权限版本() {
        service.changePermissions(manager(), new TenantCustomRolePermissionChangeCommand(8001L, Set.of("model:write")));

        assertEquals("model:write", jdbc.queryForObject("SELECT permission_code FROM organization_access.tenant_custom_role_permission WHERE custom_role_id = 8001", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM organization_access.tenant_custom_role WHERE custom_role_id = 8001", Long.class));
        assertEquals(3L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 9001", Long.class));
        assertEquals(5L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 9002", Long.class));
        assertEquals(Set.of(new MemberId(9001L), new MemberId(9002L)), Set.copyOf(sessions.revoked()));
    }

    @Test
    void 会话撤销失败时必须回滚角色权限与全部成员修订() {
        sessions.fail();

        assertThrows(IllegalStateException.class, () -> service.changePermissions(
                manager(), new TenantCustomRolePermissionChangeCommand(8001L, Set.of("model:write"))));

        assertEquals("model:read", jdbc.queryForObject("SELECT permission_code FROM organization_access.tenant_custom_role_permission WHERE custom_role_id = 8001", String.class));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM organization_access.tenant_custom_role WHERE custom_role_id = 8001", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 9001", Long.class));
        assertEquals(4L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 9002", Long.class));
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(9001L), CredentialType.PERSON, AccessChannel.CONSOLE, 2L,
                Set.of(new PermissionCode("access:manage")), "trc_postgres_custom_lifecycle");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = TenantCustomRoleLifecycleMapper.class)
    @Import({TenantCustomRoleLifecycleApplicationService.class, PostgresTenantCustomRoleLifecycleStore.class})
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
        private final ArrayList<MemberId> revoked = new ArrayList<>();

        @Override
        public void revokeTenantSessions(MemberId membershipId) {
            if (failing.get()) {
                throw new IllegalStateException("会话撤销失败");
            }
            revoked.add(membershipId);
        }

        void fail() {
            failing.set(true);
        }

        void reset() {
            failing.set(false);
            revoked.clear();
        }

        ArrayList<MemberId> revoked() {
            return revoked;
        }
    }
}
