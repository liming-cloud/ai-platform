package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.infrastructure.password.Pbkdf2PasswordAdapter;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证纯本地空库原子初始化唯一平台管理员，其他数据库状态保持失败关闭。 */
@Testcontainers
class LocalPlatformAdministratorInitializationMigrationTest {

    private static final char[] LOCAL_PASSWORD = "admin@123".toCharArray();

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24")
            .withDatabaseName("foundation_test")
            .withUsername("foundation")
            .withPassword("foundation_test_only");

    @BeforeEach
    void resetDatabase() throws Exception {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS organization_access CASCADE");
            statement.execute("DROP TABLE IF EXISTS public.flyway_schema_history");
        }
    }

    @Test
    void 空本地库必须原子建立固定平台管理员() throws Exception {
        localFlyway().migrate();

        try (var connection = connection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT s.password_hash, s.status AS subject_status, s.version,
                               a.permission_version,
                               b.role_code, b.status AS role_status
                        FROM organization_access.subject_identity s
                        JOIN organization_access.platform_subject_authorization a
                          ON a.subject_id = s.subject_id
                        JOIN organization_access.platform_role_binding b
                          ON b.subject_id = s.subject_id
                        WHERE s.login_name = 'admin'
                        """)) {
            assertTrue(rows.next());
            assertTrue(new Pbkdf2PasswordAdapter(new SecureRandom())
                    .matches(LOCAL_PASSWORD.clone(), rows.getString("password_hash")));
            assertEquals("ACTIVE", rows.getString("subject_status"));
            assertEquals(0L, rows.getLong("version"));
            assertEquals(1L, rows.getLong("permission_version"));
            assertEquals("PLATFORM_ADMINISTRATOR", rows.getString("role_code"));
            assertEquals("ACTIVE", rows.getString("role_status"));
            assertFalse(rows.next());
        }
        assertEquals(0L, countLoginName("platform.root"));
    }

    @Test
    void 标准迁移不得初始化平台管理员() throws Exception {
        standardFlyway().migrate();

        assertEquals(0L, countLoginName("admin"));
    }

    @Test
    void 已有租户时本地迁移不得初始化平台管理员() throws Exception {
        standardFlyway().migrate();
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO organization_access.tenant
                        (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                    VALUES (1001, 'existing-tenant', '既有租户', 'DISABLED', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
        }

        localFlyway().migrate();

        assertEquals(0L, countLoginName("admin"));
    }

    @Test
    void 已有主体时本地迁移不得初始化平台管理员() throws Exception {
        standardFlyway().migrate();
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO organization_access.subject_identity
                        (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                    VALUES (2001, 'existing.user', 'existing-password-hash', 'ACTIVE', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
        }

        localFlyway().migrate();

        assertEquals(0L, countLoginName("admin"));
    }

    private static Flyway localFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .locations(
                        "classpath:db/migration/organization-access",
                        "classpath:db/local/organization-access")
                .load();
    }

    private static Flyway standardFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .locations("classpath:db/migration/organization-access")
                .load();
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long countLoginName(String loginName) throws Exception {
        try (var connection = connection();
                var statement = connection.prepareStatement("""
                        SELECT count(*)
                        FROM organization_access.subject_identity
                        WHERE login_name = ?
                        """)) {
            statement.setString(1, loginName);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
