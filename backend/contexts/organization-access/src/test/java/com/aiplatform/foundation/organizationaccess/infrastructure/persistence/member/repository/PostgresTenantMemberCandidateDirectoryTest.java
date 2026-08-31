package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberCandidateMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.Instant;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证租户成员候选由真实 PostgreSQL 完成范围过滤、搜索与服务端分页。 */
@SpringJUnitConfig(PostgresTenantMemberCandidateDirectoryTest.TestApplication.class)
@Testcontainers
class PostgresTenantMemberCandidateDirectoryTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final long OTHER_TENANT = 100000000000000002L;
    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantMemberCandidateDirectory directory;

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
        insertTenant(TENANT.value(), "candidate-tenant");
        insertTenant(OTHER_TENANT, "other-tenant");
        insertSubject(4921825928971026431L, "zoe", "ACTIVE");
        insertSubject(4921825928971026432L, "anna", "ACTIVE");
        insertSubject(4921825928971026433L, "anna_x", "ACTIVE");
        insertSubject(4921825928971026434L, "bert", "DISABLED");
        insertSubject(4921825928971026435L, "claire", "ACTIVE");
        insertMembership(3382738893456539651L, TENANT.value(), 4921825928971026431L);
        insertMembership(3382738893456539652L, OTHER_TENANT, 4921825928971026435L);
    }

    @Test
    void 应排除当前租户已有成员和停用账户并由数据库返回稳定页() {
        var first = directory.searchEligible(TENANT, "", new PageQuery(1L, 2L));

        assertEquals(3L, first.totalElements());
        assertEquals(2L, first.totalPages());
        assertEquals("anna", first.items().get(0).loginName());
        assertEquals("anna_x", first.items().get(1).loginName());
        assertTrue(first.hasNext());

        var second = directory.searchEligible(TENANT, "", new PageQuery(2L, 2L));

        assertEquals(3L, second.totalElements());
        assertEquals(1, second.items().size());
        assertEquals("claire", second.items().getFirst().loginName());
        assertFalse(second.hasNext());
    }

    @Test
    void 搜索词中的通配符必须按登录名字面值匹配() {
        var result = directory.searchEligible(TENANT, "_", new PageQuery(1L, 20L));

        assertEquals(1L, result.totalElements());
        assertEquals("anna_x", result.items().getFirst().loginName());
    }

    private void insertTenant(long tenantId, String tenantCode) {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, tenantId, tenantCode, tenantCode, NOW.toString(), NOW.toString());
    }

    private void insertSubject(long subjectId, String loginName, String status) {
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, ?, 'valid-hash', ?, 0, ?::timestamptz, ?::timestamptz)
                """, subjectId, loginName, status, NOW.toString(), NOW.toString());
    }

    private void insertMembership(long membershipId, long tenantId, long subjectId) {
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version, created_at, updated_at)
                VALUES (?, ?, ?, 'TENANT_USER', 'ENABLED', 1, 0, ?::timestamptz, ?::timestamptz)
                """, membershipId, tenantId, subjectId, NOW.toString(), NOW.toString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantMemberCandidateMapper.class)
    @Import(PostgresTenantMemberCandidateDirectory.class)
    static class TestApplication {

        @Bean
        MybatisPlusInterceptor paginationInterceptor() {
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
            return interceptor;
        }
    }
}
