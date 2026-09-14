package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 真实迁移验证旧租户显式回填、容量约束和新租户必须显式写入配置。 */
@Testcontainers
class TenantQuotaMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateExistingTenants() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA IF EXISTS organization_access CASCADE");
        jdbc.execute("DROP TABLE IF EXISTS public.flyway_schema_history");
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/organization-access");
        configuration.target("202608310001").load().migrate();
        insertTenant(201L, "quota-enabled", "ENABLED");
        insertTenant(202L, "quota-disabled", "DISABLED");
        configuration.target("latest").load().migrate();
    }

    @Test
    void 应向所有既有租户显式回填默认值且不改变状态() {
        var rows = jdbc.queryForList("""
                SELECT tenant_id, knowledge_bases, documents_per_knowledge_base,
                       bytes_per_knowledge_base, max_file_bytes, max_text_bytes, version
                FROM organization_access.tenant_quota ORDER BY tenant_id
                """);

        assertEquals(2, rows.size());
        for (var row : rows) {
            assertEquals(3, row.get("knowledge_bases"));
            assertEquals(1000, row.get("documents_per_knowledge_base"));
            assertEquals(10_000_000_000L, row.get("bytes_per_knowledge_base"));
            assertEquals(50_000_000L, row.get("max_file_bytes"));
            assertEquals(1_048_576L, row.get("max_text_bytes"));
            assertEquals(0L, row.get("version"));
        }
        assertEquals("ENABLED", jdbc.queryForObject(
                "SELECT status FROM organization_access.tenant WHERE tenant_id = 201", String.class));
        assertEquals("DISABLED", jdbc.queryForObject(
                "SELECT status FROM organization_access.tenant WHERE tenant_id = 202", String.class));
    }

    @Test
    void 新租户无隐式默认值且最大容量按整数无损保存() {
        insertTenant(203L, "quota-new", "DISABLED");
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM organization_access.tenant_quota WHERE tenant_id = 203", Long.class));

        insertQuota(203L, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0);

        assertEquals(Long.MAX_VALUE, jdbc.queryForObject(
                "SELECT bytes_per_knowledge_base FROM organization_access.tenant_quota WHERE tenant_id = 203",
                Long.class));
    }

    @Test
    void 数据库应拒绝越界配额缺失归属及重复配置() {
        insertTenant(203L, "quota-invalid", "DISABLED");
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 0, 1, 100, 10, 10, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 1, 0, 100, 10, 10, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 1, 1, 0, 10, 10, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 1, 1, 100, 0, 10, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 1, 1, 100, 10, 0, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 1, 1, 100, 101, 10, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(203L, 1, 1, 100, 10, 10, -1));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(999L, 1, 1, 100, 10, 10, 0));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertQuota(201L, 1, 1, 100, 10, 10, 0));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM organization_access.tenant_quota WHERE tenant_id = 203", Long.class));
    }

    @Test
    void 配额行应跟随准确的租户锚点清理() {
        jdbc.update("DELETE FROM organization_access.tenant WHERE tenant_id = 201");

        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM organization_access.tenant_quota", Long.class));
        assertEquals(202L, jdbc.queryForObject(
                "SELECT tenant_id FROM organization_access.tenant_quota", Long.class));
    }

    private void insertTenant(long tenantId, String code, String status) {
        jdbc.update("""
                INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status)
                VALUES (?, ?, '配额迁移测试', ?)
                """, tenantId, code, status);
    }

    private void insertQuota(long tenantId, int knowledgeBases, int documents,
                             long bytes, long fileBytes, long textBytes, long version) {
        jdbc.update("""
                INSERT INTO organization_access.tenant_quota
                    (tenant_id, knowledge_bases, documents_per_knowledge_base,
                     bytes_per_knowledge_base, max_file_bytes, max_text_bytes, version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, tenantId, knowledgeBases, documents, bytes, fileBytes, textBytes, version);
    }
}
