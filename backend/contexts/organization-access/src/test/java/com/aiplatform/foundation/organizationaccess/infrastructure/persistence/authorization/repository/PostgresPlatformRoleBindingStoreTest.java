package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformRoleBindingRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        classes = PostgresPlatformRoleBindingStoreTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresPlatformRoleBindingStoreTest {

    private static final long SUBJECT_ID = 4921825928971026436L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PostgresPlatformRoleBindingStore store;

    @Autowired
    private PlatformRoleBindingRepository authorizations;

    @Autowired
    private PlatformRoleBindingApplicationService governance;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired private PlatformRoleBindingMapper modelWriteMapper;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
        jdbc.update(
                "INSERT INTO organization_access.subject_identity "
                        + "(subject_id, login_name, password_hash, status, version) "
                        + "VALUES (?, 'platform-account', 'platform-hash', 'ACTIVE', 0)",
                SUBJECT_ID);
    }

    @Test
    void 模型写入复核由当前角色及修订派生且角色撤销立即拒绝() {
        jdbc.update("INSERT INTO organization_access.platform_subject_authorization (subject_id, permission_version) VALUES (?, 3)", SUBJECT_ID);
        jdbc.update("INSERT INTO organization_access.platform_role_binding (binding_id, subject_id, role_code, status, version, created_at) VALUES (?, ?, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0, CURRENT_TIMESTAMP)", SUBJECT_ID + 1, SUBJECT_ID);
        var facts = new PostgresPlatformModelWriteFacts(modelWriteMapper);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var context = new com.aiplatform.foundation.security.identity.PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(SUBJECT_ID), com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE, 3,
                java.util.Set.of(com.aiplatform.foundation.security.identity.PlatformPermissions.SESSION_ACCESS,
                    com.aiplatform.foundation.security.identity.PlatformPermissions.MODEL_PLATFORM_WRITE), "synthetic-model-current-role");
        transaction.executeWithoutResult(status -> facts.requireCurrent(context));
        jdbc.update("UPDATE organization_access.platform_subject_authorization SET permission_version = 4 WHERE subject_id = ?", SUBJECT_ID);
        assertThrows(com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class,
                () -> transaction.executeWithoutResult(status -> facts.requireCurrent(context)));
        jdbc.update("UPDATE organization_access.platform_subject_authorization SET permission_version = 3 WHERE subject_id = ?", SUBJECT_ID);
        jdbc.update("UPDATE organization_access.platform_role_binding SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP WHERE subject_id = ?", SUBJECT_ID);
        assertThrows(com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class,
                () -> transaction.executeWithoutResult(status -> facts.requireCurrent(context)));
    }

    @Test
    void 绑定与撤销均应原子推进认证权限修订() {
        var binding = PlatformRoleBinding.bind(
                new PlatformRoleBindingId(4921825928971026437L),
                new SubjectId(SUBJECT_ID),
                PlatformRole.PLATFORM_ADMINISTRATOR,
                Instant.parse("2026-08-11T06:00:00Z"));

        store.bind(binding);

        var authorization = authorizations.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID)).orElseThrow();
        assertEquals(1L, authorization.permissionVersion());
        assertEquals(java.util.Set.of(PlatformRole.PLATFORM_ADMINISTRATOR), authorization.roles());

        store.revoke(binding.revoke(Instant.parse("2026-08-11T07:00:00Z")));

        assertTrue(authorizations.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT permission_version FROM organization_access.platform_subject_authorization "
                                + "WHERE subject_id = ?",
                        Long.class,
                        SUBJECT_ID));
    }

    @Test
    void 会话族撤销失败时不得提交角色绑定或权限修订() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> governance.bind(accountAdministrator(), SUBJECT_ID, "PLATFORM_ADMINISTRATOR"));

        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM organization_access.platform_role_binding WHERE subject_id = ?",
                        Long.class,
                        SUBJECT_ID));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM organization_access.platform_subject_authorization WHERE subject_id = ?",
                        Long.class,
                        SUBJECT_ID));
    }

    @Test
    void 启用租户成员资格必须使平台角色绑定资格计数非零() {
        jdbc.update("""
                INSERT INTO organization_access.tenant
                    (tenant_id, tenant_code, display_name, status, version)
                VALUES (100000000000000001, 'platform-role-eligibility', '平台角色资格测试租户', 'ENABLED', 0)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                    (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (3382738893456539659, 100000000000000001, ?, 'TENANT_USER', 'ENABLED', 0, 0)
                """, SUBJECT_ID);

        assertEquals(1L, store.countEnabledTenantMemberships(new SubjectId(SUBJECT_ID)));
    }

    private static PlatformRequestContext accountAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026435L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                java.util.Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_platform_role_binding");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = PlatformRoleBindingMapper.class)
    @Import({PostgresPlatformRoleBindingRepository.class, PostgresPlatformRoleBindingStore.class})
    static class TestApplication {

        @Bean
        PlatformRoleBindingApplicationService platformRoleBindingApplicationService(
                PostgresPlatformRoleBindingStore store) {
            PersonSessionFamilyRevoker unavailableRevoker = subjectId -> {
                throw new IllegalStateException("会话族撤销不可用");
            };
            return new PlatformRoleBindingApplicationService(
                    store,
                    unavailableRevoker,
                    () -> 4921825928971026437L,
                    java.time.Clock.systemUTC());
        }
    }
}
