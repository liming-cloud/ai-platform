package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleCreationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleApplicationService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
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

/** 验证自定义角色及其权限集合只由真实 PostgreSQL 原子保存。 */
@SpringJUnitConfig(PostgresTenantCustomRoleStoreTest.TestApplication.class)
@Testcontainers
class PostgresTenantCustomRoleStoreTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T04:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @org.springframework.beans.factory.annotation.Autowired
    private TenantCustomRoleApplicationService service;

    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seedTenant() {
        jdbc.update("DELETE FROM organization_access.tenant_custom_role_permission");
        jdbc.update("DELETE FROM organization_access.tenant_custom_role");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, 'custom-role', '自定义角色测试租户', 'ENABLED', 0, ?::timestamptz, ?::timestamptz)
                """, TENANT.value(), NOW.toString(), NOW.toString());
    }

    @Test
    void 应保存角色聚合和完整权限集合() {
        var view = service.create(manager(), new TenantCustomRoleCreationCommand(
                "模型观察员", Set.of("model:read", "model:write")));

        assertEquals(9001L, view.id());
        assertEquals("ACTIVE", jdbc.queryForObject("""
                SELECT status FROM organization_access.tenant_custom_role WHERE custom_role_id = ?
                """, String.class, 9001L));
        assertEquals(Set.of("model:read", "model:write"), Set.copyOf(jdbc.queryForList("""
                SELECT permission_code FROM organization_access.tenant_custom_role_permission
                WHERE custom_role_id = ? ORDER BY permission_code
                """, String.class, 9001L)));
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                Set.of(new PermissionCode("access:manage")),
                "trc_postgres_custom_role");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = TenantCustomRoleMapper.class)
    @Import({TenantCustomRoleApplicationService.class, PostgresTenantCustomRoleStore.class})
    static class TestApplication {

        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        EntityIdGenerator identifiers() {
            return () -> 9001L;
        }
    }
}
