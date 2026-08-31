package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证上线前只读预检会拒绝活动平台角色与启用租户成员资格并存。 */
@Testcontainers
class PersonnelScopeExclusivityMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    private Flyway flyway;

    @BeforeEach
    void migrateBaseline() {
        clearDatabase();
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/organization-access")
                .load();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/organization-access")
                .target("202608160001")
                .load()
                .migrate();
    }

    @AfterEach
    void cleanDatabase() {
        clearDatabase();
    }

    @Test
    void 历史冲突主体必须阻断迁移且不泄露业务标识() {
        seedSubjectAndTenant();
        execute("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (3382738893456539659, 100000000000000001, 4921825928971026434,
                    'TENANT_USER', 'ENABLED', 0, 0)
                """);
        execute("""
                INSERT INTO organization_access.platform_role_binding
                    (binding_id, subject_id, role_code, status, version)
                VALUES (4921825928971026435, 4921825928971026434,
                    'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0)
                """);

        var failure = assertThrows(RuntimeException.class, () -> flyway.migrate());
        var message = completeMessage(failure);
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("冲突主体数量：1"));
        org.junit.jupiter.api.Assertions.assertFalse(message.contains("conflicted-person"));
        org.junit.jupiter.api.Assertions.assertFalse(message.contains("scope-conflict-tenant"));
    }

    @Test
    void 无冲突数据必须允许迁移() {
        seedSubjectAndTenant();

        assertDoesNotThrow(() -> flyway.migrate());
    }

    private void seedSubjectAndTenant() {
        execute("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version)
                VALUES (4921825928971026434, 'conflicted-person', 'valid-hash', 'ACTIVE', 0)
                """);
        execute("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version)
                VALUES (100000000000000001, 'scope-conflict-tenant', '人员范围冲突租户', 'ENABLED', 0)
                """);
    }

    private static String completeMessage(Throwable throwable) {
        var message = new StringBuilder();
        for (var cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                message.append(cause.getMessage()).append('\n');
            }
        }
        return message.toString();
    }

    private void execute(String sql) {
        try (var connection = DriverManager.getConnection(
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
