package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 从真实旧版本升级；既有启用／停用资格均不能获得隐式功能或助手。 */
@Testcontainers
class MemberAccessMigrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24")
            .withDatabaseName("member_cutover_test").withUsername("foundation").withPassword("foundation_test_only");

    @Test
    void 旧资格升级保留身份归属角色状态且关闭所有成员功能并登记撤销() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/organization-access")
                .target(MigrationVersion.fromVersion("202609290001")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status, version)
                VALUES (100000000000000001, 'migration-one', '合成迁移租户一', 'ENABLED', 0),
                       (100000000000000002, 'migration-two', '合成迁移租户二', 'ENABLED', 0)
                """);
        jdbc.update("""
                INSERT INTO organization_access.subject_identity (subject_id, login_name, password_hash, status, version)
                VALUES (2001, 'migration-member', 'test-only', 'ACTIVE', 2),
                       (2002, 'migration-admin', 'test-only', 'ACTIVE', 3)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                  (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (3001, 100000000000000001, 2001, 'TENANT_USER', 'ENABLED', 1, 0),
                       (3002, 100000000000000002, 2001, 'TENANT_USER', 'DISABLED', 5, 2),
                       (3003, 100000000000000001, 2002, 'TENANT_ADMINISTRATOR', 'ENABLED', 4, 7)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding (tenant_id, membership_id, role_code, bound_at)
                VALUES (100000000000000001, 3001, 'TENANT_USER', CURRENT_TIMESTAMP),
                       (100000000000000002, 3002, 'TENANT_USER', CURRENT_TIMESTAMP),
                       (100000000000000001, 3003, 'TENANT_ADMINISTRATOR', CURRENT_TIMESTAMP)
                """);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/organization-access").load().migrate();
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM organization_access.member_conversation_features
                WHERE NOT assistant_selection AND NOT conversation AND NOT own_history AND NOT authorized_citations AND version = 0
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM organization_access.member_assistant_authorization", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM organization_access.tenant_membership WHERE subject_id = 2001 AND member_access_pending_revocation", Integer.class));
        assertEquals("DISABLED", jdbc.queryForObject("SELECT status FROM organization_access.tenant_membership WHERE membership_id = 3002", String.class));
        assertEquals(2L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 3001", Long.class));
        assertEquals(6L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 3002", Long.class));
        assertEquals(3L, jdbc.queryForObject("SELECT version FROM organization_access.tenant_membership WHERE membership_id = 3002", Long.class));
        assertEquals(4L, jdbc.queryForObject("SELECT permission_version FROM organization_access.tenant_membership WHERE membership_id = 3003 AND NOT member_access_pending_revocation", Long.class));
        assertEquals(7L, jdbc.queryForObject("SELECT version FROM organization_access.tenant_membership WHERE membership_id = 3003", Long.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM organization_access.tenant_membership_role_binding", Integer.class));
        assertEquals(2L, jdbc.queryForObject("SELECT version FROM organization_access.subject_identity WHERE subject_id = 2001", Long.class));
    }
}
