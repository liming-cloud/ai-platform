package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
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
 * 验证生命周期读取投影在真实 PostgreSQL 中按最后变更时间稳定分页且不读取受限列。
 */
@SpringBootTest(
        classes = PostgresTenantLifecycleReadAdapterTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresTenantLifecycleReadAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PostgresTenantLifecycleReadAdapter adapter;

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
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_outbox");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (4921825928971026434, 'projection-admin', 'valid-hash', 'ACTIVE', 0,
                    TIMESTAMPTZ '2026-08-03T03:00:00Z', TIMESTAMPTZ '2026-08-03T03:00:00Z')
                """);
        insertLifecycle(100000000000000001L, "projection-first", "第一投影", "2026-08-03T03:00:00Z");
        insertLifecycle(100000000000000002L, "projection-second", "第二投影", "2026-08-03T04:00:00Z");
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle_outbox
                    (event_id, tenant_id, event_type, lifecycle_status, lifecycle_version, occurred_at)
                VALUES (?, ?, 'TENANT_LIFECYCLE_CHANGED', 'ACTIVE', 1,
                    TIMESTAMPTZ '2026-08-03T05:00:00Z')
                """, 900000000000000001L, 100000000000000001L);
    }

    @Test
    void 应按最后变更时间分页并以创建时间回退() {
        var first = adapter.page(new PageQuery(1L, 1L));
        var second = adapter.page(new PageQuery(2L, 1L));

        assertEquals(2L, first.totalElements());
        assertEquals(2L, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(second.hasNext());
        assertEquals("projection-first", first.items().getFirst().tenantCode());
        assertEquals("2026-08-03T05:00:00Z", first.items().getFirst().lastChangedAt().toString());
        assertEquals("projection-second", second.items().getFirst().tenantCode());
        assertEquals(second.items().getFirst().createdAt(), second.items().getFirst().lastChangedAt());
    }

    private void insertLifecycle(long tenantId, String tenantCode, String displayName, String createdAt) {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'DISABLED', 0, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                """, tenantId, tenantCode, displayName, createdAt, createdAt);
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle
                    (tenant_id, tenant_code, display_name, initial_administrator_subject_id, status, version, created_at)
                VALUES (?, ?, ?, 4921825928971026434, 'ACTIVE', 1, CAST(? AS TIMESTAMPTZ))
                """, tenantId, tenantCode, displayName, createdAt);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantLifecycleReadMapper.class)
    @Import(PostgresTenantLifecycleReadAdapter.class)
    static class TestApplication {
    }
}
