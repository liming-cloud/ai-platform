package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformRoleBindingRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        classes = PostgresPlatformRoleBindingRepositoryTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresPlatformRoleBindingRepositoryTest {

    private static final long SUBJECT_ID = 4921825928971026436L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PlatformRoleBindingRepository bindings;

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
        jdbc.update("DELETE FROM organization_access.platform_access_permission");
        jdbc.update("DELETE FROM organization_access.platform_access_grant");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update(
                "INSERT INTO organization_access.subject_identity "
                        + "(subject_id, login_name, password_hash, status, version) "
                        + "VALUES (?, 'platform-admin', 'platform-hash', 'ACTIVE', 0)",
                SUBJECT_ID);
        jdbc.update(
                "INSERT INTO organization_access.platform_subject_authorization "
                        + "(subject_id, permission_version, version) VALUES (?, 4, 0)",
                SUBJECT_ID);
        jdbc.update(
                "INSERT INTO organization_access.platform_role_binding "
                        + "(binding_id, subject_id, role_code, status, version) "
                        + "VALUES (?, ?, 'PLATFORM_TENANT_GOVERNOR', 'ACTIVE', 0)",
                SUBJECT_ID, SUBJECT_ID);
    }

    @Test
    void 应只从启用主体的活动平台角色绑定派生最小治理权限() {
        var authorization = bindings.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID)).orElseThrow();

        assertEquals(4L, authorization.permissionVersion());
        assertEquals(java.util.Set.of(PlatformRole.PLATFORM_TENANT_GOVERNOR), authorization.roles());
    }

    @Test
    void 撤销绑定或不存在主体不得回退为平台会话资格() {
        jdbc.update(
                "UPDATE organization_access.platform_role_binding "
                        + "SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP WHERE subject_id = ?",
                SUBJECT_ID);

        assertTrue(bindings.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
        assertTrue(bindings.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID + 1)).isEmpty());
    }

    @Test
    void 停用主体不得回退为平台会话资格() {
        jdbc.update(
                "UPDATE organization_access.subject_identity SET status = 'DISABLED' WHERE subject_id = ?",
                SUBJECT_ID);

        assertTrue(bindings.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
    }

    @Test
    void 缺少活动绑定不得读取为可签发授权() {
        jdbc.update(
                "DELETE FROM organization_access.platform_role_binding WHERE subject_id = ?",
                SUBJECT_ID);

        assertTrue(bindings.findActiveAuthorizationBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PlatformRoleBindingMapper.class)
    @Import(PostgresPlatformRoleBindingRepository.class)
    static class TestApplication {
    }
}
