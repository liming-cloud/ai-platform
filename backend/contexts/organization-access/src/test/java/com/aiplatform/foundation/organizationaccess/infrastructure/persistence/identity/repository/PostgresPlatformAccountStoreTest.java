package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
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

/** 验证平台账户锁定读取与乐观更新的真实 PostgreSQL 合同。 */
@SpringBootTest(
        classes = PostgresPlatformAccountStoreTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresPlatformAccountStoreTest {

    private static final long ACCOUNT_ADMINISTRATOR = 4921825928971026434L;
    private static final long TARGET_SUBJECT = 4921825928971026435L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PostgresPlatformAccountStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformAccountGovernanceApplicationService governance;

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
        jdbc.update("DELETE FROM organization_access.subject_identity");
        insertSubject(ACCOUNT_ADMINISTRATOR, "account-admin", "ACTIVE");
        insertSubject(TARGET_SUBJECT, "target-account", "ACTIVE");
        jdbc.update(
                "INSERT INTO organization_access.platform_role_binding "
                        + "(binding_id, subject_id, role_code, status, version) "
                        + "VALUES (?, ?, 'PLATFORM_ACCOUNT_ADMINISTRATOR', 'ACTIVE', 0)",
                ACCOUNT_ADMINISTRATOR,
                ACCOUNT_ADMINISTRATOR);
    }

    @Test
    void 应锁定读取并用修订号原子禁用目标账户() {
        var target = store.lock(new SubjectId(TARGET_SUBJECT)).orElseThrow();

        store.update(target.disable());

        assertEquals(
                "DISABLED",
                jdbc.queryForObject(
                        "SELECT status FROM organization_access.subject_identity WHERE subject_id = ?",
                        String.class,
                        TARGET_SUBJECT));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT version FROM organization_access.subject_identity WHERE subject_id = ?",
                        Long.class,
                        TARGET_SUBJECT));
    }

    @Test
    void 过期账户修订不得覆盖后续治理操作() {
        var stale = store.lock(new SubjectId(TARGET_SUBJECT)).orElseThrow();
        store.update(stale.disable());

        assertThrows(IllegalStateException.class, () -> store.update(stale.disable()));
    }

    @Test
    void 应只将活动账户管理员计入最后管理员保护条件() {
        assertTrue(store.hasActiveAccountAdministratorRole(new SubjectId(ACCOUNT_ADMINISTRATOR)));
        assertEquals(1L, store.countOtherActiveAccountAdministrators(new SubjectId(TARGET_SUBJECT)));

        jdbc.update(
                "UPDATE organization_access.subject_identity SET status = 'DISABLED' WHERE subject_id = ?",
                ACCOUNT_ADMINISTRATOR);

        assertEquals(0L, store.countOtherActiveAccountAdministrators(new SubjectId(TARGET_SUBJECT)));
    }

    @Test
    void 应从真实数据库返回可恢复的账户目录分页摘要() {
        jdbc.update(
                "INSERT INTO organization_access.platform_role_binding "
                        + "(binding_id, subject_id, role_code, status, version) "
                        + "VALUES (?, ?, 'PLATFORM_OPERATOR', 'ACTIVE', 0)",
                TARGET_SUBJECT + 1L,
                TARGET_SUBJECT);
        var page = store.list(new PageQuery(1, 1));

        assertEquals(2L, page.totalElements());
        assertEquals(2L, page.totalPages());
        assertTrue(page.hasNext());
        assertEquals(TARGET_SUBJECT, page.items().getFirst().subjectId());
        assertEquals("target-account", page.items().getFirst().loginName());
        assertEquals("ACTIVE", page.items().getFirst().status());
        assertEquals(java.util.Set.of("PLATFORM_OPERATOR"), page.items().getFirst().activeRoleCodes());
    }

    @Test
    void 会话族撤销失败时不得提交账户禁用() {
        assertThrows(IllegalStateException.class, () -> governance.disable(accountAdministrator(), TARGET_SUBJECT));

        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM organization_access.subject_identity WHERE subject_id = ?",
                        String.class,
                        TARGET_SUBJECT));
    }

    private static PlatformRequestContext accountAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(ACCOUNT_ADMINISTRATOR),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                java.util.Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_platform_account");
    }

    private void insertSubject(long subjectId, String loginName, String status) {
        jdbc.update(
                "INSERT INTO organization_access.subject_identity "
                        + "(subject_id, login_name, password_hash, status, version) "
                        + "VALUES (?, ?, 'test-password-hash', ?, 0)",
                subjectId,
                loginName,
                status);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = PlatformAccountMapper.class)
    @Import(PostgresPlatformAccountStore.class)
    static class TestApplication {

        @Bean
        PlatformAccountGovernanceApplicationService platformAccountGovernanceApplicationService(
                PostgresPlatformAccountStore store) {
            PersonSessionFamilyRevoker unavailableRevoker = subjectId -> {
                throw new IllegalStateException("会话族撤销不可用");
            };
            return new PlatformAccountGovernanceApplicationService(store, unavailableRevoker);
        }
    }
}
