package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberEstablishmentMapper;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Set;
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

/** 验证成员建立只接受启用主体，并以真实 PostgreSQL 持久化无隐式权限的普通成员。 */
@SpringJUnitConfig(PostgresTenantMemberEstablishmentStoreTest.TestApplication.class)
@Testcontainers
class PostgresTenantMemberEstablishmentStoreTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private static final long ACTIVE_SUBJECT = 4921825928971026434L;
    private static final long DISABLED_SUBJECT = 4921825928971026435L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantMemberEstablishmentApplicationService service;

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
        jdbc.update("DELETE FROM organization_access.platform_role_binding");
        jdbc.update("DELETE FROM organization_access.platform_subject_authorization");
        jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding");
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'member-establishment', '成员建立测试租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT.value(), NOW.toString(), NOW.toString());
        insertSubject(ACTIVE_SUBJECT, "member-establishment-active", "ACTIVE");
        insertSubject(DISABLED_SUBJECT, "member-establishment-disabled", "DISABLED");
    }

    @Test
    void 建立成员必须原子写入普通用户资格和固定角色() {
        var result = service.establish(manager(), ACTIVE_SUBJECT);

        assertEquals("3382738893456539659", result.membershipId());
        assertEquals("ENABLED", result.status());
        assertEquals("TENANT_USER", jdbc.queryForObject("""
                SELECT membership_role FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND subject_id = ?
                """, String.class, TENANT.value(), ACTIVE_SUBJECT));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT permission_version FROM organization_access.tenant_membership
                WHERE tenant_id = ? AND subject_id = ?
                """, Long.class, TENANT.value(), ACTIVE_SUBJECT));
        assertEquals("TENANT_USER", jdbc.queryForObject("""
                SELECT role_code FROM organization_access.tenant_membership_role_binding
                WHERE tenant_id = ? AND membership_id = ?
                """, String.class, TENANT.value(), 3382738893456539659L));
    }

    @Test
    void 建立成员必须拒绝禁用主体和当前租户重复成员() {
        assertThrows(IllegalArgumentException.class,
                () -> service.establish(manager(), DISABLED_SUBJECT));
        service.establish(manager(), ACTIVE_SUBJECT);
        assertThrows(IllegalArgumentException.class,
                () -> service.establish(manager(), ACTIVE_SUBJECT));
    }

    @Test
    void 建立成员必须拒绝活动平台管理员() {
        jdbc.update("""
                INSERT INTO organization_access.platform_role_binding
                    (binding_id, subject_id, role_code, status, version, created_at)
                VALUES (4921825928971026436, ?, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0, ?::timestamptz)
                """, ACTIVE_SUBJECT, NOW.toString());

        assertThrows(OrganizationAccessRuleViolation.class,
                () -> service.establish(manager(), ACTIVE_SUBJECT));
    }

    private void insertSubject(long subjectId, String loginName, String status) {
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, ?, 'valid-hash', ?, 0, ?::timestamptz, ?::timestamptz)
                """, subjectId, loginName, status, NOW.toString(), NOW.toString());
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(ACTIVE_SUBJECT),
                new MembershipId(3382738893456539649L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                Set.of(new PermissionCode("access:manage")),
                "trc_postgres_member_establishment");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = TenantMemberEstablishmentMapper.class)
    @Import(PostgresTenantMemberEstablishmentStore.class)
    static class TestApplication {

        @Bean
        TenantMemberEstablishmentApplicationService tenantMemberEstablishmentApplicationService(
                PostgresTenantMemberEstablishmentStore store, EntityIdGenerator identifiers) {
            return new TenantMemberEstablishmentApplicationService(store, identifiers);
        }

        @Bean
        EntityIdGenerator identifiers() {
            return () -> 3382738893456539659L;
        }
    }
}
