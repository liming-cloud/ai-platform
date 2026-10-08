package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.*;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore;
import com.aiplatform.foundation.security.identity.*;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.redis.testcontainers.RedisContainer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 独立查询进程、真实 PostgreSQL／Redis 和正式 HTTP；不替换授权或助手 facade。 */
@SpringBootTest(classes = QueryRuntimeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.additional-location=file:src/main/resources/application.yml",
                "spring.flyway.enabled=true", "foundation.organization-access.enabled=false",
                "foundation.persistence.snowflake.worker-id=3", "foundation.persistence.snowflake.datacenter-id=1"})
@AutoConfigureTestRestTemplate
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class MemberQueryAccessHttpTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
            .withDatabaseName("member_query_test").withUsername("foundation").withPassword("foundation_test_only");
    @Container static final RedisContainer REDIS = new RedisContainer("redis:8.8.1-trixie");
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthenticatedSessionGateway sessions;
    @Autowired ApplicationContext application;
    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> RESPONSE = new org.springframework.core.ParameterizedTypeReference<>() {};

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getRedisHost);
        properties.add("spring.data.redis.port", REDIS::getRedisPort);
        properties.add("spring.flyway.locations", () -> "classpath:db/migration/organization-access,classpath:db/migration/knowledge-data,classpath:db/migration/model-runtime,classpath:db/migration/assistant-delivery,classpath:db/migration/trusted-query");
    }

    @Test
    void 无助手成员目录为空直接问答拒绝且查询进程没有账户写入装配() {
        jdbc.update("INSERT INTO organization_access.tenant (tenant_id, tenant_code, display_name, status, version) VALUES (1001, 'query-member-test', '合成查询租户', 'ENABLED', 0)");
        jdbc.update("""
                INSERT INTO organization_access.subject_identity (subject_id, login_name, password_hash, status, version)
                VALUES (2001, 'query-test-admin', 'test-only', 'ACTIVE', 0), (2002, 'query-test-member', 'test-only', 'ACTIVE', 0)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_lifecycle
                  (tenant_id, tenant_code, display_name, initial_administrator_subject_id, status, version, created_at)
                VALUES (1001, 'query-member-test', '合成查询租户', 2001, 'ACTIVE', 0, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership
                  (membership_id, tenant_id, subject_id, membership_role, status, permission_version, version)
                VALUES (3001, 1001, 2001, 'TENANT_ADMINISTRATOR', 'ENABLED', 0, 0), (3002, 1001, 2002, 'TENANT_USER', 'ENABLED', 0, 0)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_membership_role_binding (tenant_id, membership_id, role_code, bound_at)
                VALUES (1001, 3001, 'TENANT_ADMINISTRATOR', CURRENT_TIMESTAMP), (1001, 3002, 'TENANT_USER', CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO organization_access.tenant_feature_settings
                  (tenant_id, knowledge_management, model_governance, assistant_delivery, evaluation_governance,
                   assistant_selection, conversation, own_history, authorized_citations, version)
                VALUES (1001, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 0)
                """);
        jdbc.update("""
                INSERT INTO organization_access.member_conversation_features
                  (tenant_id, membership_id, assistant_selection, conversation, own_history, authorized_citations, version)
                VALUES (1001, 3002, TRUE, TRUE, FALSE, FALSE, 0)
                """);
        var headers = headers(3002, 2002, false);
        var directory = http.exchange(url("/query-assistants?pageNumber=1&pageSize=20"), HttpMethod.GET, new HttpEntity<>(headers), RESPONSE);
        assertEquals(HttpStatus.OK, directory.getStatusCode());
        assertEquals(0, directory.getBody().get("totalElements"));
        assertEquals(java.util.List.of(), directory.getBody().get("items"));
        var query = http.exchange(url("/query-runs"), HttpMethod.POST,
                new HttpEntity<>(Map.of("assistantVersionId", "420000000000000001", "question", "无授权合成查询"), headers), RESPONSE);
        assertEquals(HttpStatus.FORBIDDEN, query.getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trusted_query.query_run WHERE requested_by = 3002", Integer.class));
        var administrator = http.exchange(url("/query-assistants?pageNumber=1&pageSize=20"), HttpMethod.GET,
                new HttpEntity<>(headers(3001, 2001, true)), RESPONSE);
        assertEquals(HttpStatus.OK, administrator.getStatusCode());
        jdbc.update("UPDATE organization_access.tenant_feature_settings SET assistant_selection = FALSE WHERE tenant_id = 1001");
        assertEquals(HttpStatus.FORBIDDEN, http.exchange(url("/query-assistants"), HttpMethod.GET, new HttpEntity<>(headers), RESPONSE).getStatusCode());
        assertTrue(application.getBeansOfType(MemberAccountStore.class).isEmpty());
        assertTrue(application.getBeansOfType(com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller.TenantMemberStatusController.class).isEmpty());
    }

    private HttpHeaders headers(long member, long subject, boolean administrator) {
        var permissions = new java.util.HashSet<>(Set.of(new PermissionCode("assistant:query"), new PermissionCode("knowledge:read")));
        if (administrator) permissions.add(new PermissionCode("access:manage"));
        var context = new TrustedRequestContext(new TenantId(1001), new SubjectId(subject), new MembershipId(member),
                CredentialType.PERSON, AccessChannel.CONSOLE, 0, permissions, "test-real-query-member");
        var headers = new HttpHeaders(); headers.setBearerAuth(sessions.issue(context).tokenValue());
        headers.set("Idempotency-Key", "empty-member-query"); return headers;
    }
    private String url(String route) { return "http://localhost:" + port + "/api/v1" + route; }
}
