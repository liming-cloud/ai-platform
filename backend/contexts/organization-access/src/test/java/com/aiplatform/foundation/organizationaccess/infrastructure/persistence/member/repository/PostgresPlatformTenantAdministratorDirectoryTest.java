package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorQueryService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证管理员目录与候选目录在真实 PostgreSQL 中使用一致的服务端分页资格谓词。 */
@SpringJUnitConfig(PostgresPlatformTenantAdministratorDirectoryTest.TestApplication.class)
@Testcontainers
class PostgresPlatformTenantAdministratorDirectoryTest {

    private static final long TENANT = 100000000000000001L;
    private static final Instant NOW = Instant.parse("2026-08-31T05:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PlatformTenantAdministratorQueryService service;

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
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'administrator-directory', '管理员目录租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES
                    (8101, 'enabled-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz),
                    (8102, 'disabled-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz),
                    (8103, 'ordinary-member', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz),
                    (8104, 'fresh-candidate', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz),
                    (8105, 'platform-admin', 'valid-hash', 'ACTIVE', 0, ?::timestamptz, ?::timestamptz)
                """,
                NOW.toString(), NOW.toString(), NOW.toString(), NOW.toString(),
                NOW.toString(), NOW.toString(), NOW.toString(), NOW.toString(),
                NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status,
                     permission_version, version, created_at, updated_at)
                VALUES
                    (8201, ?, 8101, 'TENANT_ADMINISTRATOR', 'ENABLED', 1, 2, ?::timestamptz, ?::timestamptz),
                    (8202, ?, 8102, 'TENANT_ADMINISTRATOR', 'DISABLED', 2, 3, ?::timestamptz, ?::timestamptz),
                    (8203, ?, 8103, 'TENANT_USER', 'ENABLED', 0, 0, ?::timestamptz, ?::timestamptz)
                """,
                TENANT, NOW.toString(), NOW.toString(),
                TENANT, NOW.toString(), NOW.toString(),
                TENANT, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.platform_role_binding
                    (binding_id, subject_id, role_code, status, version, created_at)
                VALUES (8301, 8105, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0, ?::timestamptz)
                """, NOW.toString());
    }

    @Test
    void 管理员目录必须同时返回启用和停用管理员() {
        var page = service.list(platformAdministrator(), Long.toString(TENANT), "", "ALL", new PageQuery(1, 20));

        assertEquals(2L, page.totalElements());
        assertEquals(java.util.List.of("disabled-admin", "enabled-admin"),
                page.items().stream().map(item -> item.loginName()).toList());
    }

    @Test
    void 管理员目录的搜索与状态筛选必须在数据库分页前执行() {
        var enabled = service.list(
                platformAdministrator(), Long.toString(TENANT), "admin", "ENABLED", new PageQuery(1, 1));
        var disabled = service.list(
                platformAdministrator(), Long.toString(TENANT), "admin", "DISABLED", new PageQuery(1, 1));
        var missing = service.list(
                platformAdministrator(), Long.toString(TENANT), "ordinary", "ALL", new PageQuery(1, 20));

        assertEquals(1L, enabled.totalElements());
        assertEquals("enabled-admin", enabled.items().getFirst().loginName());
        assertEquals(1L, disabled.totalElements());
        assertEquals("disabled-admin", disabled.items().getFirst().loginName());
        assertEquals(0L, missing.totalElements());
        assertEquals(0L, service.list(platformAdministrator(), Long.toString(TENANT), "%", "ALL",
                new PageQuery(1, 20)).totalElements());
        assertThrows(IllegalArgumentException.class, () -> service.list(
                platformAdministrator(), Long.toString(TENANT), "", "ACTIVE", new PageQuery(1, 20)));
    }

    @Test
    void 最近安全变更来自真实身份或成员更新时间且初始凭据保持原型文案() {
        var initial = service.list(platformAdministrator(), Long.toString(TENANT), "enabled-admin", "ALL",
                new PageQuery(1, 20));
        assertEquals(null, initial.items().getFirst().lastSecurityChangeAt());
        var changedAt = NOW.plusSeconds(3600);
        jdbc.update("""
                UPDATE organization_access.subject_identity
                SET updated_at = ?::timestamptz
                WHERE subject_id = 8101
                """, changedAt.toString());

        var changed = service.list(platformAdministrator(), Long.toString(TENANT), "enabled-admin", "ALL",
                new PageQuery(1, 20));

        assertEquals(changedAt, changed.items().getFirst().lastSecurityChangeAt());
    }

    @Test
    void 候选必须排除现有管理员与平台管理员但允许提升普通成员() {
        var page = service.candidates(
                platformAdministrator(), Long.toString(TENANT), "", new PageQuery(1, 20));

        assertEquals(java.util.List.of("fresh-candidate", "ordinary-member"),
                page.items().stream().map(item -> item.loginName()).toList());
    }

    @Test
    void 候选不得展示其他租户已停用但仍占用归属的管理员() {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'second-directory', '第二租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT + 1L, NOW.toString(), NOW.toString());
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status,
                     permission_version, version, created_at, updated_at)
                VALUES (8204, ?, 8104, 'TENANT_ADMINISTRATOR', 'DISABLED', 0, 0,
                    ?::timestamptz, ?::timestamptz)
                """, TENANT + 1L, NOW.toString(), NOW.toString());

        var page = service.candidates(
                platformAdministrator(), Long.toString(TENANT), "", new PageQuery(1, 20));

        assertEquals(java.util.List.of("ordinary-member"),
                page.items().stream().map(item -> item.loginName()).toList());
    }

    @Test
    void 管理员翻页与越界页必须保留同范围总数和固定顺序() {
        var first = service.list(platformAdministrator(), Long.toString(TENANT), "", "ALL", new PageQuery(1, 1));
        var second = service.list(platformAdministrator(), Long.toString(TENANT), "", "ALL", new PageQuery(2, 1));
        var beyond = service.list(platformAdministrator(), Long.toString(TENANT), "", "ALL", new PageQuery(3, 1));

        assertEquals(2L, first.totalElements());
        assertEquals(2L, second.totalElements());
        assertEquals(2L, beyond.totalElements());
        assertEquals("disabled-admin", first.items().getFirst().loginName());
        assertEquals("8202", first.items().getFirst().membershipReference());
        assertEquals("enabled-admin", second.items().getFirst().loginName());
        assertEquals("8201", second.items().getFirst().membershipReference());
        assertEquals(java.util.List.of(), beyond.items());
    }

    @Test
    void 候选翻页与越界页必须只统计满足资格的身份() {
        var first = service.candidates(platformAdministrator(), Long.toString(TENANT), "", new PageQuery(1, 1));
        var second = service.candidates(platformAdministrator(), Long.toString(TENANT), "", new PageQuery(2, 1));
        var beyond = service.candidates(platformAdministrator(), Long.toString(TENANT), "", new PageQuery(3, 1));

        assertEquals(2L, first.totalElements());
        assertEquals(2L, second.totalElements());
        assertEquals(2L, beyond.totalElements());
        assertEquals("fresh-candidate", first.items().getFirst().loginName());
        assertEquals("8104", first.items().getFirst().accountReference());
        assertEquals("ordinary-member", second.items().getFirst().loginName());
        assertEquals("8103", second.items().getFirst().accountReference());
        assertEquals(java.util.List.of(), beyond.items());
    }

    @Test
    void 搜索后的越界页仍应保留筛选总数() {
        var administrators = service.list(
                platformAdministrator(), Long.toString(TENANT), "ADMIN", "DISABLED", new PageQuery(2, 1));
        var candidates = service.candidates(platformAdministrator(), Long.toString(TENANT), "fresh", new PageQuery(2, 1));

        assertEquals(1L, administrators.totalElements());
        assertEquals(java.util.List.of(), administrators.items());
        assertEquals(1L, candidates.totalElements());
        assertEquals(java.util.List.of(), candidates.items());
    }

    @Test
    void 两个目录的搜索必须将通配符和转义符视为字面量() {
        for (var query : java.util.List.of("%", "_", "\\")) {
            var administrators = service.list(
                    platformAdministrator(), Long.toString(TENANT), query, "ALL", new PageQuery(1, 1));
            var candidates = service.candidates(platformAdministrator(), Long.toString(TENANT), query, new PageQuery(1, 1));
            assertEquals(0L, administrators.totalElements(), query);
            assertEquals(java.util.List.of(), administrators.items(), query);
            assertEquals(0L, candidates.totalElements(), query);
            assertEquals(java.util.List.of(), candidates.items(), query);
        }
    }

    private static PlatformRequestContext platformAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(1L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(
                        PlatformPermissions.SESSION_ACCESS,
                        PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_postgres_tenant_administrator_directory");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PlatformTenantAdministratorMapper.class)
    @Import({
            PlatformTenantAdministratorQueryService.class,
            PostgresPlatformTenantAdministratorDirectory.class
    })
    static class TestApplication {
        @Bean
        MybatisPlusInterceptor paginationInterceptor() {
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
            return interceptor;
        }
    }
}
