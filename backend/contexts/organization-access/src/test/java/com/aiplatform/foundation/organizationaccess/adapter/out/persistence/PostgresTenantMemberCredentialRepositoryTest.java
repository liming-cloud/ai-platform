package com.aiplatform.foundation.organizationaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.application.port.out.TenantMemberCredentialRepository;
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

@SpringBootTest(classes = PostgresTenantMemberCredentialRepositoryTest.TestApplication.class)
@Testcontainers
class PostgresTenantMemberCredentialRepositoryTest {

    private static final long STORE_TENANT = 100000000000000001L;
    private static final long CAMPUS_TENANT = 100000000000000002L;
    private static final long STORE_MEMBERSHIP = 3382738893456539650L;
    private static final long CAMPUS_MEMBERSHIP = 3382738893456539651L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantMemberCredentialRepository repository;

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
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.membership_credential");
        jdbc.update("DELETE FROM organization_access.tenant");

        insertTenant(STORE_TENANT, "demo-store", "门店演示租户");
        insertTenant(CAMPUS_TENANT, "demo-campus", "校园演示租户");
        insertMembership(
                STORE_TENANT,
                4921825928971026434L,
                STORE_MEMBERSHIP,
                "admin",
                "store-hash",
                "ENABLED");
        insertMembership(
                CAMPUS_TENANT,
                4921825928971026435L,
                CAMPUS_MEMBERSHIP,
                "admin",
                "campus-hash",
                "ENABLED");
        insertPermission(STORE_TENANT, STORE_MEMBERSHIP, "knowledge:read");
        insertPermission(CAMPUS_TENANT, CAMPUS_MEMBERSHIP, "knowledge:write");
    }

    @Test
    void 应按租户编码隔离同名成员和权限() {
        var store = repository.findEnabled("demo-store", "admin").orElseThrow();
        var campus = repository.findEnabled("demo-campus", "admin").orElseThrow();

        assertEquals(STORE_TENANT, store.tenantId().value());
        assertEquals("store-hash", store.encodedPassword());
        assertEquals(STORE_MEMBERSHIP, store.membershipId().value());
        assertEquals(
                java.util.Set.of(new PermissionCode("knowledge:read")),
                store.permissions());

        assertEquals(CAMPUS_TENANT, campus.tenantId().value());
        assertEquals("campus-hash", campus.encodedPassword());
        assertEquals(CAMPUS_MEMBERSHIP, campus.membershipId().value());
        assertEquals(
                java.util.Set.of(new PermissionCode("knowledge:write")),
                campus.permissions());
    }

    @Test
    void 应拒绝停用成员和不存在租户且不回退到其他租户() {
        jdbc.update(
                "UPDATE organization_access.membership_credential "
                        + "SET status = 'DISABLED' WHERE tenant_id = ?",
                STORE_TENANT);

        assertTrue(repository.findEnabled("demo-store", "admin").isEmpty());
        assertTrue(repository.findEnabled("unknown-tenant", "admin").isEmpty());
        assertTrue(repository.findEnabled("demo-store", "missing").isEmpty());
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

    private void insertMembership(
            long tenantId,
            long subjectId,
            long membershipId,
            String loginName,
            String encodedPassword,
            String status) {
        jdbc.update(
                "INSERT INTO organization_access.membership_credential "
                        + "(membership_id, tenant_id, subject_id, login_name, "
                        + "password_hash, status, permission_version, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, 0)",
                membershipId,
                tenantId,
                subjectId,
                loginName,
                encodedPassword,
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.aiplatform.foundation.organizationaccess.adapter.out.persistence")
    @Import(PostgresTenantMemberCredentialRepository.class)
    static class TestApplication {
    }
}
