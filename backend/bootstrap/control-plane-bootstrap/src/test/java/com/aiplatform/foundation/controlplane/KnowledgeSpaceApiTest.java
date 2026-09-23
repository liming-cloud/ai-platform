package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.web.BrowserSessionCookieNames;
import com.redis.testcontainers.RedisContainer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final long STORE_TENANT =
            100000000000000001L;
    private static final String DEMO_PASSWORD = "Demo@2026!";

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
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
                url("/api/v1/knowledge-spaces?pageNumber=1&pageSize=20"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                SpacePage.class);
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        assertNotNull(listed.getBody());
        assertEquals(1, listed.getBody().items().size());
        assertEquals("门店商品知识", listed.getBody().items().getFirst().name());
    }

    @Test
    void 未登录和普通用户资料读取权均不能进入知识库管理契约() {
        var unauthenticated = http.getForEntity(
                url("/api/v1/knowledge-spaces"),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertNotNull(unauthenticated.getBody());
        assertEquals("PLATFORM_SECURITY.AUTHENTICATION.0001", unauthenticated.getBody().code());

        var readOnly = sessions.issue(new TrustedRequestContext(
                new TenantId(STORE_TENANT),
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("knowledge:read")),
                "trace-read-only"));
        var hidden = http.exchange(
                url("/api/v1/knowledge-spaces"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(readOnly.tokenValue())),
                ApiError.class);
        assertEquals(HttpStatus.FORBIDDEN, hidden.getStatusCode());
        assertNotNull(hidden.getBody());
        assertEquals(
                "KNOWLEDGE_DATA.AUTHORIZATION.0001",
                hidden.getBody().code());

        var headers = authorized(readOnly.tokenValue());
        headers.add("Idempotency-Key", "request-http-0002");
        var denied = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                new HttpEntity<>(Map.of("name", "不得创建"), headers),
                ApiError.class);

        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNotNull(denied.getBody());
        assertEquals("KNOWLEDGE_DATA.AUTHORIZATION.0001", denied.getBody().code());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_data.knowledge_space",
                        Integer.class));
    }

    @Test
    void 只有平台浏览器会话时不能访问或创建租户知识库() {
        var browserHeaders = platformBrowserHeaders();
        var capability = http.exchange(
                url("/api/v1/auth/browser-session"),
                HttpMethod.GET,
                new HttpEntity<>(browserHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, capability.getStatusCode());
        assertNotNull(capability.getBody());
        assertEquals(
                List.of("PLATFORM_TENANT_GOVERNANCE"),
                capability.getBody().get("workspaceCategories"));
        assertEquals(
                List.of("PLATFORM_ADMINISTRATOR"),
                capability.getBody().get("displayRoles"));

        var listed = http.exchange(
                url("/api/v1/knowledge-spaces"),
                HttpMethod.GET,
                new HttpEntity<>(browserHeaders),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, listed.getStatusCode());
        assertNotNull(listed.getBody());
        assertEquals(
                "PLATFORM_SECURITY.AUTHENTICATION.0001",
                listed.getBody().code());

        browserHeaders.add("Idempotency-Key", "request-http-platform-browser");
        var created = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                new HttpEntity<>(Map.of("name", "平台不得建立"), browserHeaders),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals(
                "PLATFORM_SECURITY.AUTHENTICATION.0001",
                created.getBody().code());
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

    @Test
    void 伪造登录状态参数和Header不得改变令牌租户范围() {
        var storeToken = login("demo-store");
        var campusToken = login("demo-campus");
        var headers = authorized(storeToken);
        headers.add("Idempotency-Key", "request-http-token-context");
        headers.add("X-Tenant-Id", "100000000000000002");
        headers.add("X-Subject-Id", "100000000000000098");
        headers.add("X-Membership-Id", "100000000000000097");
        headers.add("X-Permissions", "platform:admin");
        var request = new HttpEntity<>(
                Map.of("name", "令牌范围知识", "description", "只能归属当前令牌租户"),
                headers);

        var created = http.postForEntity(
                url("/api/v1/knowledge-spaces?tenantId=100000000000000002"
                        + "&subjectId=100000000000000098"
                        + "&membershipId=100000000000000097"
                        + "&permissionVersion=999&channel=PLATFORM"),
                request,
                SpaceResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertTrue(list(storeToken).items().stream()
                .anyMatch(item -> item.name().equals("令牌范围知识")));
        assertTrue(list(campusToken).items().stream()
                .noneMatch(item -> item.name().equals("令牌范围知识")));
    }

    private HttpHeaders platformBrowserHeaders() {
        var csrfResponse = http.getForEntity(
                url("/api/v1/auth/browser-csrf"),
                Void.class);
        assertEquals(HttpStatus.NO_CONTENT, csrfResponse.getStatusCode());
        var initialCsrfCookie = cookiePair(
                csrfResponse.getHeaders(),
                BrowserSessionCookieNames.CSRF + "=");
        var loginHeaders = sameOriginHeaders();
        loginHeaders.set(HttpHeaders.COOKIE, initialCsrfCookie);
        loginHeaders.set(
                "X-CSRF-Token",
                initialCsrfCookie.substring(initialCsrfCookie.indexOf('=') + 1));
        var login = http.exchange(
                url("/api/v1/auth/browser-sessions"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "loginName", "platform-admin",
                                "password", DEMO_PASSWORD),
                        loginHeaders),
                Void.class);
        assertEquals(HttpStatus.NO_CONTENT, login.getStatusCode());
        var platformCookie = cookiePair(
                login.getHeaders(),
                BrowserSessionCookieNames.PLATFORM_SESSION + "=");
        var refreshedCsrfCookie = cookiePair(
                login.getHeaders(),
                BrowserSessionCookieNames.CSRF + "=");
        var browserHeaders = sameOriginHeaders();
        browserHeaders.set(
                HttpHeaders.COOKIE,
                platformCookie + "; " + refreshedCsrfCookie);
        browserHeaders.set(
                "X-CSRF-Token",
                refreshedCsrfCookie.substring(refreshedCsrfCookie.indexOf('=') + 1));
        return browserHeaders;
    }

    private HttpHeaders sameOriginHeaders() {
        var headers = new HttpHeaders();
        headers.setOrigin("http://127.0.0.1:" + port);
        headers.set("Sec-Fetch-Site", "same-origin");
        return headers;
    }

    private static String cookiePair(HttpHeaders headers, String prefix) {
        return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(prefix))
                .findFirst()
                .map(value -> value.substring(0, value.indexOf(';')))
                .orElseThrow();
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
                        "loginName", "demo-campus".equals(tenantCode) ? "tenant-admin-candidate" : "admin",
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
            String id,
            String name,
            String description,
            String status,
            long version,
            Instant createdAt) {
    }

    private record SpacePage(
            List<SpaceResponse> items,
            long pageNumber,
            long pageSize,
            long totalElements,
            long totalPages,
            boolean hasPrevious,
            boolean hasNext) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
