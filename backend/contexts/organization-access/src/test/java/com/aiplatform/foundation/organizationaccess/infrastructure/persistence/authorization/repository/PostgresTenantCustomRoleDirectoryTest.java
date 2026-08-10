package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleDirectoryMapper;
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

/** 验证自定义角色目录只投影当前租户并由数据库完成稳定分页。 */
@SpringJUnitConfig(PostgresTenantCustomRoleDirectoryTest.TestApplication.class)
@Testcontainers
class PostgresTenantCustomRoleDirectoryTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24")
            .withDatabaseName("foundation_test")
            .withUsername("foundation")
            .withPassword("foundation_test_only");

    @Autowired
    private TenantCustomRoleDirectory directory;

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
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_binding");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_permission");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role");
        jdbc.update("DELETE FROM organization_access.tenant");
        insertTenant(TENANT.value(), "directory-tenant");
        insertTenant(100000000000000002L, "other-tenant");
        insertRole(701L, TENANT.value(), "问答专员", "ACTIVE", "assistant:query");
        insertRole(702L, TENANT.value(), "已停用角色", "DISABLED", "knowledge:read");
        insertRole(703L, 100000000000000002L, "其他租户角色", "ACTIVE", "access:manage");
    }

    @Test
    void 应按状态和名称稳定分页并隔离其他租户角色() {
        var page = directory.page(TENANT, new PageQuery(1L, 1L));

        assertEquals(2L, page.totalElements());
        assertEquals(2L, page.totalPages());
        assertEquals("问答专员", page.items().getFirst().name());
        assertEquals("ACTIVE", page.items().getFirst().status());
        assertEquals(java.util.List.of("assistant:query"), page.items().getFirst().permissionCodes());
        assertEquals(true, page.hasNext());
    }

    private void insertTenant(long tenantId, String tenantCode) {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, tenantId, tenantCode, tenantCode, NOW.toString(), NOW.toString());
    }

    private void insertRole(long roleId, long tenantId, String name, String status, String permission) {
        jdbc.update("""
                INSERT INTO organization_access.tenant_custom_role
                    (custom_role_id, tenant_id, role_name, status, version, created_at, disabled_at)
                VALUES (?, ?, ?, ?, 1, ?::timestamptz, CASE WHEN ? = 'DISABLED' THEN ?::timestamptz ELSE NULL END)
                """, roleId, tenantId, name, status, NOW.toString(), status, NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_custom_role_permission (custom_role_id, permission_code)
                VALUES (?, ?)
                """, roleId, permission);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantCustomRoleDirectoryMapper.class)
    @Import(PostgresTenantCustomRoleDirectory.class)
    static class TestApplication {
    }
}
