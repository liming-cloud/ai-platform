package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformAccessGrantMapper;
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
        classes = PostgresPlatformAccessGrantRepositoryTest.TestApplication.class,
        properties = "foundation.organization-access.enabled=false")
@Testcontainers
class PostgresPlatformAccessGrantRepositoryTest {

    private static final long SUBJECT_ID = 4921825928971026436L;

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private PlatformAccessGrantRepository grants;

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
        jdbc.update("DELETE FROM organization_access.platform_access_permission");
        jdbc.update("DELETE FROM organization_access.platform_access_grant");
        jdbc.update("DELETE FROM organization_access.subject_identity");
        jdbc.update(
                "INSERT INTO organization_access.subject_identity "
                        + "(subject_id, login_name, password_hash, status, version) "
                        + "VALUES (?, 'platform-admin', 'platform-hash', 'ACTIVE', 0)",
                SUBJECT_ID);
        jdbc.update(
                "INSERT INTO organization_access.platform_access_grant "
                        + "(subject_id, status, permission_version, version) VALUES (?, 'ENABLED', 4, 0)",
                SUBJECT_ID);
        jdbc.update(
                "INSERT INTO organization_access.platform_access_permission "
                        + "(subject_id, permission_code) VALUES (?, 'platform-session:access'), (?, 'tenant-governance:write')",
                SUBJECT_ID, SUBJECT_ID);
    }

    @Test
    void 应只读取启用主体的最小平台治理授权() {
        var grant = grants.findActiveBySubject(new SubjectId(SUBJECT_ID)).orElseThrow();

        assertEquals(4L, grant.permissionVersion());
        assertEquals(
                java.util.Set.of(
                        new PermissionCode("platform-session:access"),
                        new PermissionCode("tenant-governance:write")),
                grant.permissions());
    }

    @Test
    void 停用授权或不存在主体不得回退为平台会话资格() {
        jdbc.update(
                "UPDATE organization_access.platform_access_grant SET status = 'DISABLED' WHERE subject_id = ?",
                SUBJECT_ID);

        assertTrue(grants.findActiveBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
        assertTrue(grants.findActiveBySubject(new SubjectId(SUBJECT_ID + 1)).isEmpty());
    }

    @Test
    void 停用主体不得回退为平台会话资格() {
        jdbc.update(
                "UPDATE organization_access.subject_identity SET status = 'DISABLED' WHERE subject_id = ?",
                SUBJECT_ID);

        assertTrue(grants.findActiveBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
    }

    @Test
    void 缺少治理权限集合不得读取为可签发授权() {
        jdbc.update(
                "DELETE FROM organization_access.platform_access_permission WHERE subject_id = ?",
                SUBJECT_ID);

        assertTrue(grants.findActiveBySubject(new SubjectId(SUBJECT_ID)).isEmpty());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PlatformAccessGrantMapper.class)
    @Import(PostgresPlatformAccessGrantRepository.class)
    static class TestApplication {
    }
}
