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
        registry.add("foundation.persistence.snowflake.worker-id", () -> "1");
        registry.add("foundation.persistence.snowflake.datacenter-id", () -> "1");
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
        var page = directory.page(TENANT, new PageQuery(1L, 1L), "", "");

        assertEquals(2L, page.totalElements());
        assertEquals(2L, page.totalPages());
        assertEquals("anna", page.items().getFirst().loginName());
        assertEquals("DISABLED", page.items().getFirst().status());
        assertEquals(true, page.hasNext());
    }

    @Test
    void 搜索和状态筛选应在分页与总数计算前同时生效() {
        var page = directory.page(TENANT, new PageQuery(1, 1), "AN", "DISABLED");
        assertEquals(1, page.totalElements());
        assertEquals("anna", page.items().getFirst().loginName());
        assertEquals(0, directory.page(TENANT, new PageQuery(1, 20), "other", "").totalElements());
        assertEquals(0, directory.page(TENANT, new PageQuery(1, 20), "%", "").totalElements());
        assertEquals(0, directory.page(TENANT, new PageQuery(1, 20), "", "UNKNOWN").totalElements());
    }

    @Test
    void 中文角色搜索必须在计数和分页前生效() {
        assertEquals(2, directory.page(TENANT, new PageQuery(1, 1), "普通成员", "").totalElements());
        assertEquals(0, directory.page(TENANT, new PageQuery(1, 20), "租户管理员", "").totalElements());
    }

    @Test
    void 千成员目录必须保持数据库页边界总数及实际助手计数() {
        jdbc.update("""
                INSERT INTO organization_access.subject_identity (subject_id, login_name, password_hash, status, version)
                SELECT 4921825928971030000 + value, 'bulk-' || lpad(value::text, 4, '0'), 'test-only', 'ACTIVE', 0
                FROM generate_series(1, 1000) value
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                  (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                SELECT 3382738893456540000 + value, 100000000000000001, 4921825928971030000 + value,
                  'TENANT_USER', 'ENABLED', 0, 0 FROM generate_series(1, 1000) value
                """);
        jdbc.update("""
                INSERT INTO organization_access.member_assistant_authorization (tenant_id, membership_id, assistant_id)
                VALUES (100000000000000001, 3382738893456540001, 410000000000000001),
                       (100000000000000001, 3382738893456540001, 410000000000000002)
                """);
        var seen = new java.util.HashSet<String>();
        for (int number = 1; number <= 10; number++) {
            var page = directory.page(TENANT, new PageQuery(number, 100), "bulk-", "ENABLED");
            assertEquals(1000, page.totalElements());
            assertEquals(100, page.items().size());
            if (number == 1) assertEquals(2, page.items().getFirst().assistantCount());
            page.items().forEach(member -> org.junit.jupiter.api.Assertions.assertTrue(seen.add(member.membershipId())));
        }
        assertEquals(1000, seen.size());
        var beyond = directory.page(TENANT, new PageQuery(11, 100), "bulk-", "ENABLED");
        assertEquals(1000, beyond.totalElements());
        assertEquals(0, beyond.items().size());
    }

    @Test
    void 超出尾页仍应返回同范围总数且无重复成员() {
        var second = directory.page(TENANT, new PageQuery(2, 1), "", "");
        assertEquals("zoe", second.items().getFirst().loginName());
        var overflow = directory.page(TENANT, new PageQuery(3, 1), "", "");
        assertEquals(2, overflow.totalElements());
        assertEquals(0, overflow.items().size());
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
                VALUES (?, ?, ?, 'TENANT_USER', ?, 1, 0, ?::timestamptz, ?::timestamptz)
                """, membershipId, tenantId, subjectId, status, NOW.toString(), NOW.toString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantMemberDirectoryMapper.class)
    @Import(PostgresTenantMemberDirectory.class)
    static class TestApplication {
        @org.springframework.context.annotation.Bean
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor pagination() {
            var interceptor = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(
                    com.baomidou.mybatisplus.annotation.DbType.POSTGRE_SQL));
            return interceptor;
        }
    }
}
