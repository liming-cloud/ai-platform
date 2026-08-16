package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleBindingCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleBindingMapper;
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

/** 验证自定义角色绑定、成员权限修订和会话撤销只经 PostgreSQL 事务一起提交。 */
@SpringJUnitConfig(PostgresTenantCustomRoleBindingStoreTest.TestApplication.class)
@Testcontainers
class PostgresTenantCustomRoleBindingStoreTest {
    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId MEMBER = new MemberId(3382738893456539650L);
    private static final Instant NOW = Instant.parse("2026-08-10T06:00:00Z");
    @Container private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24").withDatabaseName("foundation_test").withUsername("foundation").withPassword("foundation_test_only");
    @Autowired private TenantCustomRoleBindingApplicationService service;
    @Autowired private FailingSessionRevoker sessions;
    @Autowired private JdbcTemplate jdbc;
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", POSTGRES::getJdbcUrl); registry.add("spring.datasource.username", POSTGRES::getUsername); registry.add("spring.datasource.password", POSTGRES::getPassword); registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access"); }
    @BeforeEach void seed() {
        sessions.reset();
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_binding");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_permission");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status, version) VALUES (?, 'custom-binding', '自定义绑定测试', 'ENABLED', 0)", TENANT.value());
        jdbc.update("INSERT INTO organization_access.subject_identity (subject_id, login_name, password_hash, status, version) VALUES (4921825928971026434, 'binding-admin', 'valid-hash', 'ACTIVE', 0)");
        jdbc.update("INSERT INTO organization_access.tenant_membership (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version) VALUES (?, ?, 4921825928971026434, 'TENANT_USER', 'ENABLED', 2, 3)", MEMBER.value(), TENANT.value());
        jdbc.update("INSERT INTO organization_access.tenant_custom_role (custom_role_id, tenant_id, role_name, status, version, created_at) VALUES (9001, ?, '模型观察员', 'ACTIVE', 0, ?::timestamptz)", TENANT.value(), NOW.toString());
        jdbc.update("INSERT INTO organization_access.tenant_custom_role_permission (custom_role_id, permission_code) VALUES (9001, 'model:read')");
    }
    @Test void 应原子写入绑定并推进成员权限版本() {
        service.bind(manager(), new TenantCustomRoleBindingCommand(MEMBER.value(), 9001L));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM organization_access.tenant_custom_role_binding", Long.class));
        assertEquals(3L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = ?", Long.class, MEMBER.value()));
    }
    @Test void 会话撤销失败时不得留下绑定或权限版本修订() {
        sessions.fail();

        assertThrows(IllegalStateException.class,
                () -> service.bind(manager(), new TenantCustomRoleBindingCommand(MEMBER.value(), 9001L)));

        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM organization_access.tenant_custom_role_binding", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = ?", Long.class, MEMBER.value()));
    }

    private static TrustedRequestContext manager() { return new TrustedRequestContext(TENANT, new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L), new MembershipId(MEMBER.value()), CredentialType.PERSON, AccessChannel.CONSOLE, 2L, Set.of(new PermissionCode("access:manage")), "trc_postgres_custom_binding"); }
    @SpringBootConfiguration @EnableAutoConfiguration @EnableTransactionManagement @MapperScan(basePackageClasses = TenantCustomRoleBindingMapper.class) @Import({TenantCustomRoleBindingApplicationService.class, PostgresTenantCustomRoleBindingStore.class}) static class TestApplication { @Bean Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } @Bean FailingSessionRevoker sessionRevoker() { return new FailingSessionRevoker(); } }
    static final class FailingSessionRevoker implements TenantMemberSessionRevoker {
        private final AtomicBoolean failing = new AtomicBoolean();
        @Override public void revokeTenantSessions(MemberId membershipId) { if (failing.get()) { throw new IllegalStateException("会话撤销失败"); } }
        void fail() { failing.set(true); }
        void reset() { failing.set(false); }
    }
}
