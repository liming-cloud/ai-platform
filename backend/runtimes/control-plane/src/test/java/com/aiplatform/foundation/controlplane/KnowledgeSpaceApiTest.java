package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.redis.testcontainers.RedisContainer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
class KnowledgeSpaceApiTest {

    private static final UUID STORE_TENANT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Container
    private static final RedisContainer REDIS = new RedisContainer("redis:8.8.1-trixie");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthenticatedSessionGateway sessions;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @BeforeEach
    void clearKnowledgeSpaces() {
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
    }

    @Test
    void 登录后创建重放并列出当前租户知识空间() {
        var token = login("demo-store");
        var headers = authorized(token);
        headers.add("Idempotency-Key", "request-http-0001");
        var request = new HttpEntity<>(
                Map.of(
                        "name", "门店商品知识",
                        "description", "供门店机器人使用"),
                headers);

        var created = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                request,
                SpaceResponse.class);
        var replayed = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                request,
                SpaceResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.CREATED, replayed.getStatusCode());
        assertNotNull(created.getBody());
        assertNotNull(replayed.getBody());
        assertEquals(created.getBody().id(), replayed.getBody().id());
        assertEquals("true", replayed.getHeaders().getFirst("Idempotency-Replayed"));

        var listed = http.exchange(
                url("/api/v1/knowledge-spaces?limit=20"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                SpacePage.class);
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        assertNotNull(listed.getBody());
        assertEquals(1, listed.getBody().items().size());
        assertEquals("门店商品知识", listed.getBody().items().getFirst().name());
    }

    @Test
    void 未登录和缺少写权限均失败且不创建资源() {
        var unauthenticated = http.getForEntity(
                url("/api/v1/knowledge-spaces"),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertNotNull(unauthenticated.getBody());
        assertEquals("AUTHENTICATION_REQUIRED", unauthenticated.getBody().code());

        var readOnly = sessions.issue(new TrustedRequestContext(
                new TenantId(STORE_TENANT),
                new SubjectId(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                new MembershipId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("knowledge:read")),
                "trace-read-only"));
        var headers = authorized(readOnly.tokenValue());
        headers.add("Idempotency-Key", "request-http-0002");
        var denied = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                new HttpEntity<>(Map.of("name", "不得创建"), headers),
                ApiError.class);

        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNotNull(denied.getBody());
        assertEquals("ACCESS_DENIED", denied.getBody().code());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_data.knowledge_space",
                        Integer.class));
    }

    @Test
    void 两个演示租户的列表相互隔离() {
        var storeToken = login("demo-store");
        var campusToken = login("demo-campus");
        create(storeToken, "request-http-store", "门店知识");
        create(campusToken, "request-http-campus", "校园知识");

        var store = list(storeToken);
        var campus = list(campusToken);

        assertEquals(List.of("门店知识"), store.items().stream().map(SpaceResponse::name).toList());
        assertEquals(List.of("校园知识"), campus.items().stream().map(SpaceResponse::name).toList());
        assertFalse(store.items().stream().anyMatch(item -> item.name().equals("校园知识")));
        assertTrue(campus.items().stream().noneMatch(item -> item.name().equals("门店知识")));
    }

    private void create(String token, String idempotencyKey, String name) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", idempotencyKey);
        var response = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                new HttpEntity<>(Map.of("name", name), headers),
                SpaceResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private SpacePage list(String token) {
        var response = http.exchange(
                url("/api/v1/knowledge-spaces"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                SpacePage.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private String login(String tenantCode) {
        var response = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", tenantCode,
                        "loginName", "admin",
                        "password", "Demo@2026!"),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private static HttpHeaders authorized(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record SessionResponse(String tokenValue) {
    }

    private record SpaceResponse(
            UUID id,
            String name,
            String description,
            String status,
            long version,
            Instant createdAt) {
    }

    private record SpacePage(List<SpaceResponse> items, String nextCursor) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
