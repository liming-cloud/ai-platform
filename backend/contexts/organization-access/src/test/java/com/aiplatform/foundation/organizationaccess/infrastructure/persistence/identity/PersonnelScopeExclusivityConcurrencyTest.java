package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberStatusApplicationService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresPlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberStatusMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证共同主体行锁会串行化平台角色绑定与租户成员恢复。 */
@SpringBootTest(
        classes = PersonnelScopeExclusivityConcurrencyTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PersonnelScopeExclusivityConcurrencyTest {

    private static final long SUBJECT_ID = 4921825928971026434L;
    private static final long MEMBERSHIP_ID = 3382738893456539659L;
    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-31T01:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PlatformRoleBindingApplicationService platformRoles;

    @Autowired
    private TenantMemberStatusApplicationService tenantMembers;

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
        jdbc.update("DELETE FROM organization_access.membership_permission");
        jdbc.update("DELETE FROM organization_access.tenant_membership");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update("DELETE FROM organization_access.tenant");
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version)
                VALUES (?, 'personnel-scope-concurrency', '人员范围并发测试租户', 'ENABLED', 0)
                """, TENANT_ID.value());
        jdbc.update("""
                INSERT INTO organization_access.subject_identity
                    (subject_id, login_name, password_hash, status, version)
                VALUES (?, 'personnel-scope-target', 'valid-hash', 'ACTIVE', 0)
                """, SUBJECT_ID);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (?, ?, ?, 'TENANT_USER', 'DISABLED', 0, 0)
                """, MEMBERSHIP_ID, TENANT_ID.value(), SUBJECT_ID);
    }

    @Test
    void 并发绑定平台角色与恢复租户成员只能成功一个() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var platformResult = executor.submit(() -> executeAfterGate(
                    ready,
                    start,
                    () -> platformRoles.bind(
                            platformAdministrator(), SUBJECT_ID, "PLATFORM_ADMINISTRATOR")));
            var tenantResult = executor.submit(() -> executeAfterGate(
                    ready,
                    start,
                    () -> tenantMembers.enable(tenantAdministrator(), MEMBERSHIP_ID)));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            var outcomes = java.util.Arrays.asList(
                    platformResult.get(10, TimeUnit.SECONDS),
                    tenantResult.get(10, TimeUnit.SECONDS));
            assertEquals(1L, outcomes.stream().filter(java.util.Objects::isNull).count());
            var rejection = outcomes.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            assertInstanceOf(OrganizationAccessRuleViolation.class, rejection);
        }

        var activePlatformRoles = jdbc.queryForObject("""
                SELECT count(*) FROM organization_access.platform_role_binding
                WHERE subject_id = ? AND status = 'ACTIVE'
                """, Long.class, SUBJECT_ID);
        var enabledMemberships = jdbc.queryForObject("""
                SELECT count(*) FROM organization_access.tenant_membership
                WHERE subject_id = ? AND status = 'ENABLED'
                """, Long.class, SUBJECT_ID);
        assertEquals(1L, activePlatformRoles + enabledMemberships);
    }

    private static Throwable executeAfterGate(
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingOperation operation) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("并发测试启动超时");
            }
            operation.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private static PlatformRequestContext platformAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_personnel_scope_platform");
    }

    private static TrustedRequestContext tenantAdministrator() {
        return new TrustedRequestContext(
                TENANT_ID,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026437L),
                new MembershipId(3382738893456539658L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                Set.of(new PermissionCode("access:manage")),
                "trc_personnel_scope_tenant");
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {PlatformRoleBindingMapper.class, TenantMemberStatusMapper.class})
    @Import({PostgresPlatformRoleBindingStore.class, PostgresTenantMemberStatusStore.class})
    static class TestApplication {

        @Bean
        PlatformRoleBindingApplicationService platformRoleBindingApplicationService(
                PostgresPlatformRoleBindingStore store) {
            return new PlatformRoleBindingApplicationService(
                    store, subjectId -> { }, () -> 4921825928971026438L,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Bean
        TenantMemberStatusApplicationService tenantMemberStatusApplicationService(
                PostgresTenantMemberStatusStore store) {
            return new TenantMemberStatusApplicationService(store, memberId -> { });
        }
    }
}
