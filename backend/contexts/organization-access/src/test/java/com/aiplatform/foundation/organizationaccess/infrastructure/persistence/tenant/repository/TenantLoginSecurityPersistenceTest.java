package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLoginSecuritySettingsService;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginSecurityPolicyMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginAttemptMapper;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 真实 PostgreSQL 验证策略版本与并发安全的失败观察窗口。 */
@SpringBootTest(classes = TenantLoginSecurityPersistenceTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class TenantLoginSecurityPersistenceTest {
    private static final TenantId TENANT = new TenantId(201L);
    private static final TenantId OTHER = new TenantId(202L);
    private static final SubjectId SUBJECT = new SubjectId(301L);
    private static final Instant START = Instant.parse("2026-09-28T00:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine3.24")
            .withDatabaseName("foundation_test").withUsername("foundation")
            .withPassword(UUID.randomUUID().toString());

    @Autowired private TenantLoginSecurityPolicyStore policies;
    @Autowired private TenantLoginAttemptStore attempts;
    @Autowired private TenantLoginSecuritySettingsService settings;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM organization_access.tenant");
        for (long id : new long[]{TENANT.value(), OTHER.value()}) {
            jdbc.update("INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status) VALUES (?, ?, '安全测试', 'DISABLED')",
                    id, "security-" + id);
            jdbc.update("INSERT INTO organization_access.tenant_login_security_policy (tenant_id, session_hours, failed_attempts, lock_minutes, version) VALUES (?, 8, 5, 15, 0)", id);
        }
    }

    @Test
    void 只替换目标租户且版本旧值拒绝() {
        var first = policies.find(TENANT.value()).orElseThrow();
        assertEquals(8, first.sessionHours());
        assertTrue(policies.replace(new TenantLoginSecurityPolicy(TENANT, 4, 3, 30, 1), 0));
        assertFalse(policies.replace(new TenantLoginSecurityPolicy(TENANT, 6, 4, 20, 1), 0));
        assertEquals(4, policies.find(TENANT.value()).orElseThrow().sessionHours());
        assertEquals(8, policies.find(OTHER.value()).orElseThrow().sessionHours());
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE organization_access.tenant_login_security_policy SET session_hours = 25 WHERE tenant_id = ?",
                TENANT.value()));
    }

    @Test
    void 固定观察窗口达到阈值后锁定且其他租户不受影响() {
        var policy = new TenantLoginSecurityPolicy(TENANT, 8, 3, 30, 0);
        assertFalse(attempts.isLocked(TENANT, SUBJECT, START));
        attempts.recordFailure(TENANT, SUBJECT, policy, START);
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(60));
        assertTrue(attempts.clearIfUnlocked(TENANT, SUBJECT, START.plusSeconds(61)));
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(120));
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(180));
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(240));
        assertTrue(attempts.isLocked(TENANT, SUBJECT, START.plusSeconds(241)));
        assertFalse(attempts.clearIfUnlocked(TENANT, SUBJECT, START.plusSeconds(241)));
        assertFalse(attempts.isLocked(OTHER, SUBJECT, START.plusSeconds(241)));
        assertTrue(attempts.clearIfUnlocked(TENANT, SUBJECT, START.plusSeconds(2040)));
        assertFalse(attempts.isLocked(TENANT, SUBJECT, START.plusSeconds(2041)));
    }

    @Test
    void 十五分钟观察窗口过期后重新计数() {
        var policy = new TenantLoginSecurityPolicy(TENANT, 8, 3, 5, 0);
        attempts.recordFailure(TENANT, SUBJECT, policy, START);
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(60));
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(16 * 60));
        assertFalse(attempts.isLocked(TENANT, SUBJECT, START.plusSeconds(16 * 60)));
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(16 * 60 + 1));
        assertFalse(attempts.isLocked(TENANT, SUBJECT, START.plusSeconds(16 * 60 + 1)));
        attempts.recordFailure(TENANT, SUBJECT, policy, START.plusSeconds(16 * 60 + 2));
        assertTrue(attempts.isLocked(TENANT, SUBJECT, START.plusSeconds(16 * 60 + 2)));
    }

    @Test
    void 撤销目标会话失败时策略版本和数值必须回滚() {
        var platform = new PlatformRequestContext(new SubjectId(401L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 0,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_security_rollback");

        assertThrows(IllegalStateException.class, () -> settings.update(platform, TENANT.value(), 4, 3, 5, 0));
        var unchanged = policies.find(TENANT.value()).orElseThrow();
        assertEquals(8, unchanged.sessionHours());
        assertEquals(5, unchanged.failedAttempts());
        assertEquals(0, unchanged.version());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantLoginSecurityPolicyMapper.class)
    static class TestApplication {
        @Bean TenantLoginSecurityPolicyStore policyStore(TenantLoginSecurityPolicyMapper mapper) {
            return new PostgresTenantLoginSecurityPolicyStore(mapper);
        }
        @Bean TenantLoginAttemptStore attemptStore(TenantLoginAttemptMapper mapper) {
            return new PostgresTenantLoginAttemptStore(mapper);
        }
        @Bean TenantSessionRevoker sessionRevoker() {
            return tenantId -> { throw new IllegalStateException("模拟会话撤销不可用"); };
        }
        @Bean TenantLoginSecuritySettingsService settings(TenantLoginSecurityPolicyStore store,
                TenantSessionRevoker revoker) {
            return new TenantLoginSecuritySettingsService(store, revoker);
        }
    }
}
