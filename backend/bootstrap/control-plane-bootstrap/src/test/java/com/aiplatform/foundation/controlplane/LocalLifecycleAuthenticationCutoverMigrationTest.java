package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证仅空本地库能够初始化生命周期认证切换，已有租户绝不被本地初始化改写。 */
@Testcontainers
class LocalLifecycleAuthenticationCutoverMigrationTest {

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
    void 空本地库必须建立生命周期认证切换事实() throws Exception {
        localFlyway().migrate();

        assertEquals(1L, countCutovers());
    }

    @Test
    void 已有租户时本地初始化不得建立生命周期认证切换事实() throws Exception {
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

        assertEquals(0L, countCutovers());
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

    private static long countCutovers() throws Exception {
        try (var connection = connection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT count(*) FROM organization_access.tenant_lifecycle_authentication_cutover")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
