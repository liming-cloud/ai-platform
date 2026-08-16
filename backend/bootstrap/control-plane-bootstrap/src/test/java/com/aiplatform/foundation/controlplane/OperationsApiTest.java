package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.evaluationoperations.application.operations.service.OperationsQueryService;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.ConcurrentQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.DailyQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.application.operations.query.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryAuditRecordView;
import com.aiplatform.foundation.evaluationoperations.application.operations.query.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.application.operations.dto.QueryUsageView;
import com.aiplatform.foundation.evaluationoperations.application.operations.policy.OperationsPermissionDeniedException;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.redis.testcontainers.RedisContainer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
class OperationsApiTest {

    private static final long STORE =
            100000000000000001L;
    private static final Instant FROM = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-28T00:00:00Z");
    private static final String QUERY_RUN_ID = "430000000000000001";
    private static final String REQUESTED_BY = "730000000000000001";
    private static final String ASSISTANT_VERSION_ID = "420000000000000001";

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

    @MockitoBean
    private OperationsQueryService operations;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @BeforeEach
    void resetOperations() {
        reset(operations);
    }

    @Test
    void 租户管理员可按时间范围读取查询用量() {
        when(operations.usage(any())).thenReturn(usage());
        var token = login();

        var response = http.exchange(
                url("/api/v1/usage?from=" + FROM + "&to=" + TO),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                QueryUsageView.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(usage(), response.getBody());
        var captured = ArgumentCaptor.forClass(QueryUsageQuery.class);
        verify(operations).usage(captured.capture());
        assertEquals(STORE, captured.getValue().context().tenantId().value());
        assertEquals(FROM, captured.getValue().from());
        assertEquals(TO, captured.getValue().to());
    }

    @Test
    void 租户管理员可使用页码读取脱敏审计记录() {
        when(operations.audit(any())).thenReturn(auditPage());
        var token = login();

        var response = http.exchange(
                url("/api/v1/audit-records?pageNumber=2&pageSize=5"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                new ParameterizedTypeReference<PageResult<QueryAuditRecordView>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(auditPage(), response.getBody());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().items().isEmpty());
        var captured = ArgumentCaptor.forClass(ListQueryAuditRecordsQuery.class);
        verify(operations).audit(captured.capture());
        assertEquals(STORE, captured.getValue().context().tenantId().value());
        assertEquals(2, captured.getValue().page().pageNumber());
        assertEquals(5, captured.getValue().page().pageSize());
    }

    @Test
    void 未登录或无权限时不得读取运营事实() {
        var unauthenticated = http.getForEntity(
                url("/api/v1/usage"),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertNotNull(unauthenticated.getBody());
        assertEquals("PLATFORM_SECURITY.AUTHENTICATION.0001", unauthenticated.getBody().code());

        when(operations.usage(any()))
                .thenThrow(new OperationsPermissionDeniedException());
        var denied = http.exchange(
                url("/api/v1/usage"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(login())),
                ApiError.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNotNull(denied.getBody());
        assertEquals("EVALUATION_OPERATIONS.AUTHORIZATION.0001", denied.getBody().code());
    }

    @Test
    void 平台与租户管理员可读取各自边界内的脱敏运营总览() {
        var platformOverview = http.exchange(
                url("/api/v1/platform/operations/overview?range=DAY_7"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(platformLogin())),
                String.class);
        assertEquals(HttpStatus.OK, platformOverview.getStatusCode());
        assertSafeOverview(platformOverview.getBody());
        assertTrue(platformOverview.getBody().contains("\"attentionTenantCount\""));

        var tenantPage = http.exchange(
                url("/api/v1/platform/operations/tenants?range=DAY_7&page=1&size=1"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(platformLogin())),
                String.class);
        assertEquals(HttpStatus.OK, tenantPage.getStatusCode());
        assertSafeOverview(tenantPage.getBody());
        assertTrue(tenantPage.getBody().contains("\"pageNumber\":1"));
        assertTrue(tenantPage.getBody().contains("\"pageSize\":1"));

        var tenantOverview = http.exchange(
                url("/api/v1/operations/overview?range=DAY_7"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(login())),
                String.class);
        assertEquals(HttpStatus.OK, tenantOverview.getStatusCode());
        assertSafeOverview(tenantOverview.getBody());
        assertTrue(tenantOverview.getBody().contains("\"quota\""));
    }

    @Test
    void 平台与租户运营入口不得跨会话边界访问() {
        var tenantToPlatform = http.exchange(
                url("/api/v1/platform/operations/overview"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(login())),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, tenantToPlatform.getStatusCode());

        var platformToTenant = http.exchange(
                url("/api/v1/operations/overview"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(platformLogin())),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, platformToTenant.getStatusCode());
    }

    private String login() {
        var response = http.postForEntity(
                url("/api/v1/auth/sessions"),
                Map.of(
                        "tenantCode", "demo-store",
                        "loginName", "admin",
                        "password", "Demo@2026!"),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private String platformLogin() {
        var response = http.postForEntity(
                url("/api/v1/platform/auth/sessions"),
                Map.of(
                        "loginName", "platform-admin",
                        "password", "Demo@2026!"),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private static void assertSafeOverview(String body) {
        assertNotNull(body);
        assertFalse(body.contains("tenantId"));
        assertFalse(body.contains("subjectId"));
        assertFalse(body.contains("membershipId"));
        assertFalse(body.contains("loginName"));
        assertFalse(body.contains("traceId"));
        assertFalse(body.contains("details"));
    }

    private static QueryUsageView usage() {
        return new QueryUsageView(
                FROM,
                TO,
                7,
                4,
                1,
                1,
                1,
                11,
                13,
                24,
                new DailyQueryQuotaView(7, 100, TO),
                new ConcurrentQueryQuotaView(1, 4),
                Instant.parse("2026-07-27T08:00:00Z"));
    }

    private static PageResult<QueryAuditRecordView> auditPage() {
        return PageResult.of(
                List.of(new QueryAuditRecordView(
                        QUERY_RUN_ID,
                        REQUESTED_BY,
                        ASSISTANT_VERSION_ID,
                        "a".repeat(64),
                        "ANSWERED",
                        null,
                        11,
                        13,
                        24,
                        FROM.plusSeconds(1),
                        FROM.plusSeconds(2))),
                new PageQuery(2, 5),
                8);
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

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
