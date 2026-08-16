package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.repository.PostgresTenantMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 证明 PostgreSQL 只在全局主体表保存凭据，并按主体恢复租户成员资格。
 */
@SpringBootTest(
        classes = PostgresSubjectAuthenticationRepositoryTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresSubjectAuthenticationRepositoryTest {

    private static final long STORE_TENANT = 100000000000000001L;
    private static final long CAMPUS_TENANT = 100000000000000002L;
    private static final long ADMIN_SUBJECT = 4921825928971026434L;
    private static final long STORE_MEMBERSHIP = 3382738893456539650L;
    private static final long CAMPUS_MEMBERSHIP = 3382738893456539651L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private SubjectIdentityRepository identities;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seedFacts() {
        jdbc.update("DELETE FROM organization_access.tenant_membership_role_binding");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_authentication_cutover");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_outbox");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle");
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");

        insertTenant(STORE_TENANT, "demo-store", "门店演示租户");
        insertTenant(CAMPUS_TENANT, "demo-campus", "校园演示租户");
        insertSubject(ADMIN_SUBJECT, "admin", "shared-hash", "ACTIVE");
        insertMembership(STORE_TENANT, STORE_MEMBERSHIP, ADMIN_SUBJECT, "ENABLED");
        insertMembership(CAMPUS_TENANT, CAMPUS_MEMBERSHIP, ADMIN_SUBJECT, "ENABLED");
        insertPermission(STORE_TENANT, STORE_MEMBERSHIP, "knowledge:read");
        insertPermission(CAMPUS_TENANT, CAMPUS_MEMBERSHIP, "knowledge:write");
    }

    @Test
    void 应以唯一全局主体凭据恢复隔离的租户成员资格() {
        var subject = identities.findActiveByLogin("admin").orElseThrow();
        var store = memberships.findEnabled("demo-store", subject.id()).orElseThrow();
        var campus = memberships.findEnabled("demo-campus", subject.id()).orElseThrow();

        assertEquals(new SubjectId(ADMIN_SUBJECT), subject.id());
        assertEquals("shared-hash", subject.encodedPassword().verificationValue());
        assertEquals(STORE_MEMBERSHIP, store.membershipId().value());
        assertEquals(CAMPUS_MEMBERSHIP, campus.membershipId().value());
        assertEquals(
                java.util.Set.of(new PermissionCode("knowledge:read")),
                store.permissions());
        assertEquals(
                java.util.Set.of(new PermissionCode("knowledge:write")),
                campus.permissions());
    }

    @Test
    void 应拒绝已停用主体且不回退到成员资格中的凭据() {
        jdbc.update(
                "UPDATE organization_access.subject_identity SET status = 'DISABLED' WHERE subject_id = ?",
                ADMIN_SUBJECT);

        assertTrue(identities.findActiveByLogin("admin").isEmpty());
        assertTrue(memberships.findEnabled("demo-store", new SubjectId(999L)).isEmpty());
    }

    @Test
    void 认证切换后必须只以活跃生命周期签发租户会话资格() {
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle
                    (tenant_id, tenant_code, display_name, initial_administrator_subject_id, status, version, created_at)
                VALUES (?, 'demo-store', '门店演示租户', ?, 'ACTIVE', 1, TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, STORE_TENANT, ADMIN_SUBJECT);
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle
                    (tenant_id, tenant_code, display_name, initial_administrator_subject_id, status, version, created_at)
                VALUES (?, 'demo-campus', '校园演示租户', ?, 'SUSPENDED', 2, TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, CAMPUS_TENANT, ADMIN_SUBJECT);
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle_authentication_cutover
                    (singleton_key, activated_at)
                VALUES (TRUE, TIMESTAMPTZ '2026-08-03T03:00:00Z')
                """);

        assertTrue(memberships.findEnabled("demo-store", new SubjectId(ADMIN_SUBJECT)).isPresent());
        assertTrue(memberships.findEnabled("demo-campus", new SubjectId(ADMIN_SUBJECT)).isEmpty());
    }

    @Test
    void 普通用户应从固定角色恢复问答和知识读取权限() {
        insertRoleBinding(STORE_TENANT, STORE_MEMBERSHIP, "TENANT_USER");

        var profile = memberships.findEnabled("demo-store", new SubjectId(ADMIN_SUBJECT)).orElseThrow();

        assertEquals(
                java.util.Set.of(
                        new PermissionCode("knowledge:read"),
                        new PermissionCode("assistant:query")),
                profile.permissions());
    }

    private void insertTenant(long tenantId, String code, String name) {
        jdbc.update(
                "INSERT INTO organization_access.tenant "
                        + "(tenant_id, tenant_code, display_name, status, version) "
                        + "VALUES (?, ?, ?, 'ENABLED', 0)",
                tenantId,
                code,
                name);
    }

    private void insertSubject(long subjectId, String loginName, String passwordHash, String status) {
        jdbc.update(
                "INSERT INTO organization_access.subject_identity "
                        + "(subject_id, login_name, password_hash, status, version) "
                        + "VALUES (?, ?, ?, ?, 0)",
                subjectId,
                loginName,
                passwordHash,
                status);
    }

    private void insertMembership(
            long tenantId,
            long membershipId,
            long subjectId,
            String status) {
        jdbc.update(
                "INSERT INTO organization_access.tenant_membership "
                        + "(membership_id, tenant_id, subject_id, membership_role, "
                        + "status, permission_version, version) "
                        + "VALUES (?, ?, ?, 'TENANT_USER', ?, 1, 0)",
                membershipId,
                tenantId,
                subjectId,
                status);
    }

    private void insertPermission(long tenantId, long membershipId, String permissionCode) {
        jdbc.update(
                "INSERT INTO organization_access.membership_permission "
                        + "(tenant_id, membership_id, permission_code) VALUES (?, ?, ?)",
                tenantId,
                membershipId,
                permissionCode);
    }

    private void insertRoleBinding(long tenantId, long membershipId, String roleCode) {
        jdbc.update(
                "INSERT INTO organization_access.tenant_membership_role_binding "
                        + "(tenant_id, membership_id, role_code, bound_at) "
                        + "VALUES (?, ?, ?, TIMESTAMPTZ '2026-08-10T00:00:00Z')",
                tenantId,
                membershipId,
                roleCode);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = {SubjectIdentityMapper.class, TenantMembershipMapper.class})
    @Import({PostgresSubjectIdentityRepository.class, PostgresTenantMembershipRepository.class})
    static class TestApplication {
    }
}
