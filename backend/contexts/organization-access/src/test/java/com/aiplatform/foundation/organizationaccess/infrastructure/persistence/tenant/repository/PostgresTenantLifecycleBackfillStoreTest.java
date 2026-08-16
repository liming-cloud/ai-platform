package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantInitialAdministratorMapping;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 验证历史租户回填只能以一个真实 PostgreSQL 事务提交生命周期、成员角色和最小事件。
 */
@SpringBootTest(
        classes = PostgresTenantLifecycleBackfillStoreTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresTenantLifecycleBackfillStoreTest {

    private static final long ACTIVE_TENANT = 100000000000000001L;
    private static final long SUSPENDED_TENANT = 100000000000000002L;
    private static final long ACTIVE_SUBJECT = 4921825928971026434L;
    private static final long SUSPENDED_SUBJECT = 4921825928971026435L;
    private static final long ACTIVE_MEMBERSHIP = 3382738893456539650L;
    private static final long SUSPENDED_MEMBERSHIP = 3382738893456539651L;
    private static final Instant BACKFILLED_AT = Instant.parse("2026-08-03T02:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private TenantLifecycleBackfillService backfills;

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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_tenant_lifecycle_outbox ON organization_access.tenant_lifecycle_outbox");
        jdbc.execute("DROP FUNCTION IF EXISTS organization_access.fail_tenant_lifecycle_outbox()");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_authentication_cutover");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle_outbox");
        jdbc.update("DELETE FROM organization_access.tenant_lifecycle");
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");

        insertTenant(ACTIVE_TENANT, "demo-store", "演示门店", "ENABLED");
        insertTenant(SUSPENDED_TENANT, "demo-campus", "演示校园", "DISABLED");
        insertSubject(ACTIVE_SUBJECT, "store-admin", "ACTIVE");
        insertSubject(SUSPENDED_SUBJECT, "campus-admin", "ACTIVE");
        insertMembership(ACTIVE_MEMBERSHIP, ACTIVE_TENANT, ACTIVE_SUBJECT);
        insertMembership(SUSPENDED_MEMBERSHIP, SUSPENDED_TENANT, SUSPENDED_SUBJECT);
    }

    @Test
    void 完整人工映射应以单一事务写入合法状态链成员角色和最小事件() {
        backfills.backfill(List.of(
                mapping(ACTIVE_TENANT, ACTIVE_SUBJECT),
                mapping(SUSPENDED_TENANT, SUSPENDED_SUBJECT)), BACKFILLED_AT);

        assertEquals(List.of("ACTIVE", "SUSPENDED"), jdbc.queryForList("""
                SELECT status
                FROM organization_access.tenant_lifecycle
                ORDER BY tenant_id
                """, String.class));
        assertEquals(List.of(1L, 2L), jdbc.queryForList("""
                SELECT version
                FROM organization_access.tenant_lifecycle
                ORDER BY tenant_id
                """, Long.class));
        assertEquals(List.of("TENANT_ADMINISTRATOR", "TENANT_ADMINISTRATOR"), jdbc.queryForList("""
                SELECT membership_role
                FROM organization_access.tenant_membership
                ORDER BY tenant_id
                """, String.class));
        assertEquals(List.of(
                        "PROVISIONING",
                        "ACTIVE",
                        "PROVISIONING",
                        "ACTIVE",
                        "SUSPENDED"),
                jdbc.queryForList("""
                        SELECT lifecycle_status
                        FROM organization_access.tenant_lifecycle_outbox
                        ORDER BY tenant_id, lifecycle_version
                        """, String.class));
        assertEquals(1L, count("organization_access.tenant_lifecycle_authentication_cutover"));
    }

    @Test
    void 缺失映射必须使整批回滚且不留下根成员角色或事件() {
        assertThrows(IllegalArgumentException.class, () -> backfills.backfill(
                List.of(mapping(ACTIVE_TENANT, ACTIVE_SUBJECT)), BACKFILLED_AT));

        assertEquals(0L, count("organization_access.tenant_lifecycle"));
        assertEquals(0L, count("organization_access.tenant_lifecycle_outbox"));
        assertEquals(0L, count("organization_access.tenant_lifecycle_authentication_cutover"));
        assertEquals(List.of("TENANT_USER", "TENANT_USER"), jdbc.queryForList("""
                SELECT membership_role
                FROM organization_access.tenant_membership
                ORDER BY tenant_id
                """, String.class));
    }

    @Test
    void 重复租户映射必须在锁定前拒绝整批回填() {
        assertThrows(IllegalArgumentException.class, () -> backfills.backfill(List.of(
                mapping(ACTIVE_TENANT, ACTIVE_SUBJECT),
                mapping(ACTIVE_TENANT, ACTIVE_SUBJECT),
                mapping(SUSPENDED_TENANT, SUSPENDED_SUBJECT)), BACKFILLED_AT));

        assertNoBackfillResidue();
    }

    @Test
    void 跨租户主体映射必须整批失败关闭() {
        assertThrows(IllegalArgumentException.class, () -> backfills.backfill(List.of(
                mapping(ACTIVE_TENANT, SUSPENDED_SUBJECT),
                mapping(SUSPENDED_TENANT, SUSPENDED_SUBJECT)), BACKFILLED_AT));

        assertNoBackfillResidue();
    }

    @Test
    void 禁用主体映射必须整批失败关闭() {
        jdbc.update("UPDATE organization_access.subject_identity SET status = 'DISABLED' WHERE subject_id = ?", ACTIVE_SUBJECT);

        assertThrows(IllegalStateException.class, () -> backfills.backfill(List.of(
                mapping(ACTIVE_TENANT, ACTIVE_SUBJECT),
                mapping(SUSPENDED_TENANT, SUSPENDED_SUBJECT)), BACKFILLED_AT));

        assertNoBackfillResidue();
    }

    @Test
    void 非成员主体映射必须整批失败关闭() {
        insertSubject(4921825928971026436L, "outsider", "ACTIVE");

        assertThrows(IllegalArgumentException.class, () -> backfills.backfill(List.of(
                mapping(ACTIVE_TENANT, 4921825928971026436L),
                mapping(SUSPENDED_TENANT, SUSPENDED_SUBJECT)), BACKFILLED_AT));

        assertNoBackfillResidue();
    }

    @Test
    void 发件箱写入失败必须回滚已经写入的生命周期和成员角色() {
        jdbc.execute("""
                CREATE FUNCTION organization_access.fail_tenant_lifecycle_outbox()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF NEW.lifecycle_status = 'SUSPENDED' THEN
                        RAISE EXCEPTION '受控发件箱故障';
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_tenant_lifecycle_outbox
                BEFORE INSERT ON organization_access.tenant_lifecycle_outbox
                FOR EACH ROW
                EXECUTE FUNCTION organization_access.fail_tenant_lifecycle_outbox()
                """);

        assertThrows(RuntimeException.class, () -> backfills.backfill(List.of(
                mapping(ACTIVE_TENANT, ACTIVE_SUBJECT),
                mapping(SUSPENDED_TENANT, SUSPENDED_SUBJECT)), BACKFILLED_AT));

        assertNoBackfillResidue();
    }

    private TenantInitialAdministratorMapping mapping(long tenantId, long subjectId) {
        return new TenantInitialAdministratorMapping(new TenantId(tenantId), new SubjectId(subjectId));
    }

    private long count(String tableName) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tableName, Long.class);
    }

    private void assertNoBackfillResidue() {
        assertEquals(0L, count("organization_access.tenant_lifecycle"));
        assertEquals(0L, count("organization_access.tenant_lifecycle_outbox"));
        assertEquals(0L, count("organization_access.tenant_lifecycle_authentication_cutover"));
        assertEquals(List.of("TENANT_USER", "TENANT_USER"), jdbc.queryForList("""
                SELECT membership_role
                FROM organization_access.tenant_membership
                ORDER BY tenant_id
                """, String.class));
    }

    private void insertTenant(long tenantId, String code, String name, String status) {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0, TIMESTAMPTZ '2026-07-01T00:00:00Z', TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, tenantId, code, name, status);
    }

    private void insertSubject(long subjectId, String loginName, String status) {
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version, created_at, updated_at)
                VALUES (?, ?, 'valid-hash', ?, 0, TIMESTAMPTZ '2026-07-01T00:00:00Z', TIMESTAMPTZ '2026-07-01T00:00:00Z')
                """, subjectId, loginName, status);
    }

    private void insertMembership(long membershipId, long tenantId, long subjectId) {
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (?, ?, ?, 'TENANT_USER', 'ENABLED', 3, 7)
                """, membershipId, tenantId, subjectId);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantLifecycleBackfillMapper.class)
    @Import({TenantLifecycleBackfillService.class, PostgresTenantLifecycleBackfillStore.class})
    static class TestApplication {

        @Bean
        EntityIdGenerator testIdentifiers() {
            var values = new AtomicLong(900000000000000000L);
            return values::incrementAndGet;
        }
    }
}
