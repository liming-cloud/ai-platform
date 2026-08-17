package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 验证初始租户管理员候选的真实 PostgreSQL 资格边界。 */
@Testcontainers
@org.springframework.boot.test.context.SpringBootTest(
        classes = JdbcPlatformAccountCandidateDirectoryTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
class JdbcPlatformAccountCandidateDirectoryTest {

    private static final long PLATFORM_ROOT = 4921825928971026501L;
    private static final long TENANT_ADMINISTRATOR = 4921825928971026502L;
    private static final long DISABLED_ACCOUNT = 4921825928971026503L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private JdbcPlatformAccountCandidateDirectory directory;

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
        jdbc.update("DELETE FROM organization_access.subject_identity");
        insertSubject(PLATFORM_ROOT, "platform.root", "ACTIVE");
        insertSubject(TENANT_ADMINISTRATOR, "tenant.administrator", "ACTIVE");
        insertSubject(DISABLED_ACCOUNT, "disabled.account", "DISABLED");
        jdbc.update(
                "INSERT INTO organization_access.platform_role_binding "
                        + "(binding_id, subject_id, role_code, status, version) "
                        + "VALUES (?, ?, 'PLATFORM_ADMINISTRATOR', 'ACTIVE', 0)",
                PLATFORM_ROOT,
                PLATFORM_ROOT);
    }

    @Test
    void 初始租户管理员候选必须排除平台管理员和停用账户() {
        var page = directory.search("", new PageQuery(1L, 20L));

        assertEquals(1L, page.totalElements());
        assertEquals("tenant.administrator", page.items().getFirst().loginName());
        assertEquals("ACTIVE", page.items().getFirst().status());
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
    @Import(JdbcPlatformAccountCandidateDirectory.class)
    static class TestApplication {
    }
}
