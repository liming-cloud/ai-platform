package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberDirectoryMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证成员目录只投影当前租户并由数据库完成稳定分页。 */
@SpringJUnitConfig(PostgresTenantMemberDirectoryTest.TestApplication.class)
@Testcontainers
class PostgresTenantMemberDirectoryTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantMemberDirectory directory;

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
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        insertTenant(TENANT.value(), "directory-tenant");
        insertTenant(100000000000000002L, "other-tenant");
        insertSubject(4921825928971026434L, "zoe");
        insertSubject(4921825928971026435L, "anna");
        insertSubject(4921825928971026436L, "other");
        insertMembership(3382738893456539650L, TENANT.value(), 4921825928971026434L, "ENABLED");
        insertMembership(3382738893456539651L, TENANT.value(), 4921825928971026435L, "DISABLED");
        insertMembership(3382738893456539652L, 100000000000000002L, 4921825928971026436L, "ENABLED");
    }

    @Test
    void 应按登录名稳定分页并隔离其他租户成员() {
        var page = directory.page(TENANT, new PageQuery(1L, 1L));

        assertEquals(2L, page.totalElements());
        assertEquals(2L, page.totalPages());
        assertEquals("anna", page.items().getFirst().loginName());
        assertEquals("DISABLED", page.items().getFirst().status());
        assertEquals(true, page.hasNext());
    }

    private void insertTenant(long tenantId, String tenantCode) {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, tenantId, tenantCode, tenantCode, NOW.toString(), NOW.toString());
    }

    private void insertSubject(long subjectId, String loginName) {
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, ?, 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz)
                """, subjectId, loginName, NOW.toString(), NOW.toString());
    }

    private void insertMembership(long membershipId, long tenantId, long subjectId, String status) {
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 'MEMBER', ?, 1, 0, ?::timestamptz, ?::timestamptz)
                """, membershipId, tenantId, subjectId, status, NOW.toString(), NOW.toString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantMemberDirectoryMapper.class)
    @Import(PostgresTenantMemberDirectory.class)
    static class TestApplication {
    }
}
