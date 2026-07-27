package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.evaluationoperations.api.OperationsQueryUseCase;
import com.aiplatform.foundation.evaluationoperations.api.model.ConcurrentQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.api.model.DailyQueryQuotaView;
import com.aiplatform.foundation.evaluationoperations.api.model.ListQueryAuditRecordsQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordPage;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryAuditRecordView;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageQuery;
import com.aiplatform.foundation.evaluationoperations.api.model.QueryUsageView;
import com.aiplatform.foundation.evaluationoperations.application.OperationsPermissionDeniedException;
import com.redis.testcontainers.RedisContainer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static final UUID STORE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant FROM = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-28T00:00:00Z");
    private static final UUID QUERY_RUN_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID REQUESTED_BY =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ASSISTANT_VERSION_ID =
            UUID.fromString("82000000-0000-0000-0000-000000000001");

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
    private OperationsQueryUseCase operations;

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
    void 租户管理员可使用游标读取脱敏审计记录() {
        when(operations.audit(any())).thenReturn(auditPage());
        var token = login();

        var response = http.exchange(
                url("/api/v1/audit-records?cursor=下一页&limit=5"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                QueryAuditRecordPage.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(auditPage(), response.getBody());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().items().isEmpty());
        var captured = ArgumentCaptor.forClass(ListQueryAuditRecordsQuery.class);
        verify(operations).audit(captured.capture());
        assertEquals(STORE, captured.getValue().context().tenantId().value());
        assertEquals("下一页", captured.getValue().cursor());
        assertEquals(5, captured.getValue().limit());
    }

    @Test
    void 未登录或无权限时不得读取运营事实() {
        var unauthenticated = http.getForEntity(
                url("/api/v1/usage"),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertNotNull(unauthenticated.getBody());
        assertEquals("AUTHENTICATION_REQUIRED", unauthenticated.getBody().code());

        when(operations.usage(any()))
                .thenThrow(new OperationsPermissionDeniedException());
        var denied = http.exchange(
                url("/api/v1/usage"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(login())),
                ApiError.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNotNull(denied.getBody());
        assertEquals("ACCESS_DENIED", denied.getBody().code());
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

    private static QueryAuditRecordPage auditPage() {
        return new QueryAuditRecordPage(
                List.of(new QueryAuditRecordView(
                        QUERY_RUN_ID,
                        REQUESTED_BY.toString(),
                        ASSISTANT_VERSION_ID,
                        "a".repeat(64),
                        "ANSWERED",
                        null,
                        11,
                        13,
                        24,
                        FROM.plusSeconds(1),
                        FROM.plusSeconds(2))),
                "后续页");
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
