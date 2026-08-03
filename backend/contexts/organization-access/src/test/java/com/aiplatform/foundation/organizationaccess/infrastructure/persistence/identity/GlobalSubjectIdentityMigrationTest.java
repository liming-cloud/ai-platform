package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 验证历史成员凭据只能在无歧义时迁移为全局主体凭据。
 */
@Testcontainers
class GlobalSubjectIdentityMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    private Flyway flyway;

    @BeforeEach
    void migrateLegacySchema() {
        clearDatabase();
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/organization-access")
                .load();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/organization-access")
                .target("202607270001")
                .load()
                .migrate();
    }

    @AfterEach
    void cleanDatabase() {
        clearDatabase();
    }

    @Test
    void 应将同一主体的历史凭据迁移一次并移除成员凭据列() {
        insertLegacyMembership(101L, 11L, 91L, "admin", "same-hash", "ENABLED");
        insertLegacyMembership(102L, 12L, 91L, "admin", "same-hash", "ENABLED");

        flyway.migrate();

        assertEquals(
                1,
                queryForInt("SELECT COUNT(*) FROM organization_access.subject_identity "
                        + "WHERE subject_id = 91 AND login_name = 'admin'"));
        assertEquals(
                0,
                queryForInt("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'organization_access' "
                        + "AND table_name = 'tenant_membership' "
                        + "AND column_name IN ('login_name', 'password_hash')"));
    }

    @Test
    void 歧义登录名必须拒绝迁移而不是猜测合并主体() {
        insertLegacyMembership(101L, 11L, 91L, "admin", "same-hash", "ENABLED");
        insertLegacyMembership(102L, 12L, 92L, "admin", "other-hash", "ENABLED");

        assertThrows(RuntimeException.class, () -> flyway.migrate());
    }

    private void insertLegacyMembership(
            long tenantId,
            long membershipId,
            long subjectId,
            String loginName,
            String passwordHash,
            String status) {
        execute("INSERT INTO organization_access.tenant "
                + "(tenant_id, tenant_code, display_name, status, version) "
                + "VALUES (" + tenantId + ", 'tenant-" + tenantId + "', '租户', 'ENABLED', 0)");
        execute("INSERT INTO organization_access.membership_credential "
                + "(membership_id, tenant_id, subject_id, login_name, password_hash, "
                + "status, permission_version, version) VALUES ("
                + membershipId + ", " + tenantId + ", " + subjectId + ", '" + loginName
                + "', '" + passwordHash + "', '" + status + "', 0, 0)");
    }

    private int queryForInt(String sql) {
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void execute(String sql) {
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void clearDatabase() {
        execute("DROP SCHEMA IF EXISTS organization_access CASCADE");
        execute("DROP TABLE IF EXISTS public.flyway_schema_history");
    }
}
