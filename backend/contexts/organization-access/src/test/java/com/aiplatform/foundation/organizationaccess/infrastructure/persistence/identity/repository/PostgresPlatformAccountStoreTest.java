package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.infrastructure.password.Pbkdf2PasswordAdapter;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.SecureRandom;
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
    private static final AtomicBoolean REVOCATION_FAILURE = new AtomicBoolean();

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

    @Autowired
    private Pbkdf2PasswordAdapter passwords;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access");
    }

    @BeforeEach
    void seedFacts() {
        REVOCATION_FAILURE.set(true);
        jdbc.update("DELETE FROM organization_access.platform_role_binding");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        insertSubject(ACCOUNT_ADMINISTRATOR, "account-admin", "ACTIVE");
        insertSubject(TARGET_SUBJECT, "target-account", "ACTIVE");
        jdbc.update(
                "INSERT INTO organization_access.platform_role_binding "
                        + "(binding_id, subject_id, role_code, status, version) "
                        + "VALUES (?, ?, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0)",
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
    void 应由真实数据库唯一约束建立全局账户且拒绝并发重复登录名() {
        var created = SubjectIdentity.register(
                new SubjectId(TARGET_SUBJECT + 10L),
                "new-account",
                new EncodedPassword("test-password-hash"),
                java.time.Instant.parse("2026-08-11T04:00:00Z"));
        var duplicate = SubjectIdentity.register(
                new SubjectId(TARGET_SUBJECT + 11L),
                "new-account",
                new EncodedPassword("test-password-hash"),
                java.time.Instant.parse("2026-08-11T04:00:01Z"));

        assertTrue(store.insert(created));
        assertFalse(store.insert(duplicate));

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_access.subject_identity WHERE login_name = 'new-account'",
                Long.class));
        assertEquals(TARGET_SUBJECT + 10L, jdbc.queryForObject(
                "SELECT subject_id FROM organization_access.subject_identity WHERE login_name = 'new-account'",
                Long.class));
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
                        + "VALUES (?, ?, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0)",
                TARGET_SUBJECT + 1L,
                TARGET_SUBJECT);
        var page = store.list(new PageQuery(1, 1));

        assertEquals(2L, page.totalElements());
        assertEquals(2L, page.totalPages());
        assertTrue(page.hasNext());
        assertEquals(TARGET_SUBJECT, page.items().getFirst().subjectId());
        assertEquals("target-account", page.items().getFirst().loginName());
        assertEquals("ACTIVE", page.items().getFirst().status());
        assertEquals(java.util.Set.of("PLATFORM_ADMINISTRATOR"), page.items().getFirst().activeRoleCodes());
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

    @Test
    void 会话族撤销失败时不得提交账户启用() {
        jdbc.update(
                "UPDATE organization_access.subject_identity SET status = 'DISABLED', version = 1 WHERE subject_id = ?",
                TARGET_SUBJECT);

        assertThrows(IllegalStateException.class, () -> governance.enable(accountAdministrator(), TARGET_SUBJECT));

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
    void 会话族撤销失败时不得提交账户密码重置() {
        var before = jdbc.queryForObject(
                "SELECT password_hash FROM organization_access.subject_identity WHERE subject_id = ?",
                String.class,
                TARGET_SUBJECT);

        assertThrows(IllegalStateException.class, () -> governance.resetPassword(
                accountAdministrator(), TARGET_SUBJECT, "new-password".toCharArray()));

        assertEquals(before, jdbc.queryForObject(
                "SELECT password_hash FROM organization_access.subject_identity WHERE subject_id = ?",
                String.class,
                TARGET_SUBJECT));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT version FROM organization_access.subject_identity WHERE subject_id = ?",
                Long.class,
                TARGET_SUBJECT));
    }

    @Test
    void 密码重置应在真实事务内提交新摘要和修订并使新口令可验证() {
        REVOCATION_FAILURE.set(false);
        var newPassword = "new-password".toCharArray();

        governance.resetPassword(accountAdministrator(), TARGET_SUBJECT, newPassword);

        var hash = jdbc.queryForObject(
                "SELECT password_hash FROM organization_access.subject_identity WHERE subject_id = ?",
                String.class,
                TARGET_SUBJECT);
        assertTrue(passwords.matches("new-password".toCharArray(), hash));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT version FROM organization_access.subject_identity WHERE subject_id = ?",
                Long.class,
                TARGET_SUBJECT));
        assertEquals(new String(new char[newPassword.length]), new String(newPassword));
    }

    @Test
    void 启用应在真实事务内提交状态和修订() {
        jdbc.update(
                "UPDATE organization_access.subject_identity SET status = 'DISABLED', version = 1 WHERE subject_id = ?",
                TARGET_SUBJECT);
        REVOCATION_FAILURE.set(false);

        governance.enable(accountAdministrator(), TARGET_SUBJECT);

        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM organization_access.subject_identity WHERE subject_id = ?",
                        String.class,
                        TARGET_SUBJECT));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT version FROM organization_access.subject_identity WHERE subject_id = ?",
                        Long.class,
                        TARGET_SUBJECT));
    }

    @Test
    void 空目录应返回零总数而不是虚构第一页数据() {
        jdbc.update("DELETE FROM organization_access.platform_role_binding");
        jdbc.update("DELETE FROM organization_access.subject_identity");

        var page = store.list(new PageQuery(1, 20));

        assertTrue(page.items().isEmpty());
        assertEquals(0L, page.totalElements());
        assertEquals(0L, page.totalPages());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());

        var stalePage = store.list(new PageQuery(2, 20));
        assertTrue(stalePage.items().isEmpty());
        assertEquals(0L, stalePage.totalElements());
        assertFalse(stalePage.hasPrevious());
        assertFalse(stalePage.hasNext());
    }

    @Test
    void 相同创建时间的跨页结果应按主体编号稳定排列() {
        jdbc.update("UPDATE organization_access.subject_identity SET created_at = '2026-09-14T00:00:00Z'");

        var first = store.list(new PageQuery(1, 1));
        var second = store.list(new PageQuery(2, 1));
        var beyond = store.list(new PageQuery(3, 1));

        assertEquals(TARGET_SUBJECT, first.items().getFirst().subjectId());
        assertEquals(ACCOUNT_ADMINISTRATOR, second.items().getFirst().subjectId());
        assertEquals(2L, second.totalElements());
        assertEquals(2L, second.totalPages());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
        assertTrue(beyond.items().isEmpty());
        assertEquals(3, beyond.pageNumber());
        assertEquals(2L, beyond.totalElements());
        assertFalse(beyond.hasNext());
    }

    @Test
    void 历史角色绑定不能重复计算账户总数或泄漏密码摘要() {
        jdbc.update("INSERT INTO organization_access.platform_role_binding "
                        + "(binding_id, subject_id, role_code, status, version, revoked_at) "
                        + "VALUES (?, ?, 'PLATFORM_ADMINISTRATOR', 'REVOKED', 1, CURRENT_TIMESTAMP)",
                TARGET_SUBJECT + 100L, ACCOUNT_ADMINISTRATOR);

        var page = store.list(new PageQuery(1, 20));
        var account = page.items().stream()
                .filter(item -> item.subjectId() == ACCOUNT_ADMINISTRATOR).findFirst().orElseThrow();

        assertEquals(2L, page.totalElements());
        assertEquals(2, page.items().size());
        assertEquals(java.util.Set.of("PLATFORM_ADMINISTRATOR"),
                account.activeRoleCodes());
        assertFalse(page.toString().contains("test-password-hash"));
    }

    @Test
    void 千条账户目录应有界分页并记录当前环境查询耗时() {
        jdbc.update("INSERT INTO organization_access.subject_identity "
                        + "(subject_id, login_name, password_hash, status, version) "
                        + "SELECT ? + n, 'scale-account-' || n, 'test-password-hash', 'ACTIVE', 0 "
                        + "FROM generate_series(1, 1000) n",
                TARGET_SUBJECT + 1000L);
        for (var index = 0; index < 10; index++) {
            store.list(new PageQuery(1, 20));
        }
        var elapsedNanos = new long[30];
        for (var index = 0; index < elapsedNanos.length; index++) {
            var started = System.nanoTime();
            var page = store.list(new PageQuery(index + 1, 20));
            elapsedNanos[index] = System.nanoTime() - started;
            assertEquals(1002L, page.totalElements());
            assertEquals(20, page.items().size());
            assertEquals(51L, page.totalPages());
        }
        java.util.Arrays.sort(elapsedNanos);
        // 耗时仅作为当前容器环境的实测，不把不稳定的墙钟阈值作为单元测试断言。
        System.out.println("平台账户分页实测：1002 账户，每页 20，预热 10 次，样本 30，p95_ms="
                + elapsedNanos[28] / 1_000_000.0);
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
        MybatisPlusInterceptor paginationInterceptor() {
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
            return interceptor;
        }

        @Bean
        Pbkdf2PasswordAdapter pbkdf2PasswordAdapter() {
            return new Pbkdf2PasswordAdapter(new SecureRandom());
        }

        @Bean
        PlatformAccountGovernanceApplicationService platformAccountGovernanceApplicationService(
                PostgresPlatformAccountStore store, Pbkdf2PasswordAdapter passwords) {
            PersonSessionFamilyRevoker unavailableRevoker = subjectId -> {
                if (REVOCATION_FAILURE.get()) {
                    throw new IllegalStateException("会话族撤销不可用");
                }
            };
            return new PlatformAccountGovernanceApplicationService(store, unavailableRevoker, passwords);
        }
    }
}
