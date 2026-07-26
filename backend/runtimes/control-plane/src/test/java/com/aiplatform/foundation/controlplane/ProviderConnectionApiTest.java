package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redis.testcontainers.RedisContainer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class ProviderConnectionApiTest {

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

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @BeforeEach
    void clearProviderConnections() {
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
    }

    @Test
    void 租户管理员可创建重放探测并列出不含密钥的连接() {
        var token = login("demo-store");
        var headers = authorized(token);
        headers.add("Idempotency-Key", "provider-http-0001");
        var request = new HttpEntity<>(
                Map.of(
                        "providerKind", "DETERMINISTIC",
                        "displayName", "演示模型连接",
                        "endpoint", "memory://deterministic",
                        "region", "本地",
                        "authenticationType", "API_KEY",
                        "credentialReference", "secret://demo/provider"),
                headers);

        var created = http.postForEntity(
                url("/api/v1/model-providers"),
                request,
                ProviderResponse.class);
        var replayed = http.postForEntity(
                url("/api/v1/model-providers"),
                request,
                ProviderResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.CREATED, replayed.getStatusCode());
        assertNotNull(created.getBody());
        assertNotNull(replayed.getBody());
        assertEquals(created.getBody().id(), replayed.getBody().id());
        assertEquals("true", replayed.getHeaders().getFirst("Idempotency-Replayed"));
        assertTrue(created.getBody().credentialConfigured());

        var rawList = http.exchange(
                url("/api/v1/model-providers"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                String.class);
        assertEquals(HttpStatus.OK, rawList.getStatusCode());
        assertNotNull(rawList.getBody());
        assertFalse(rawList.getBody().contains("secret://demo/provider"));
        assertFalse(rawList.getBody().contains("credentialReference"));

        var probe = http.postForEntity(
                url("/api/v1/model-providers/" + created.getBody().id() + "/probe"),
                new HttpEntity<>(
                        Map.of(
                                "expectedVersion", 0,
                                "modelCode", "deterministic-chat",
                                "timeoutMillis", 2000),
                        authorized(token)),
                ProbeResponse.class);
        assertEquals(HttpStatus.OK, probe.getStatusCode());
        assertNotNull(probe.getBody());
        assertTrue(probe.getBody().reachable());
        assertEquals("VERIFIED", probe.getBody().providerConnection().status());
        assertEquals(List.of("CHAT", "EMBEDDING"), probe.getBody().capabilities().stream()
                .sorted()
                .toList());
    }

    @Test
    void 两个演示租户的供应商连接列表相互隔离() {
        var storeToken = login("demo-store");
        var campusToken = login("demo-campus");
        create(storeToken, "provider-http-store", "门店模型");
        create(campusToken, "provider-http-campus", "校园模型");

        var store = list(storeToken);
        var campus = list(campusToken);

        assertEquals(List.of("门店模型"), store.items().stream()
                .map(ProviderResponse::displayName)
                .toList());
        assertEquals(List.of("校园模型"), campus.items().stream()
                .map(ProviderResponse::displayName)
                .toList());
    }

    @Test
    void 未登录不能读取供应商连接() {
        var response = http.getForEntity(
                url("/api/v1/model-providers"),
                ApiError.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTHENTICATION_REQUIRED", response.getBody().code());
    }

    private void create(String token, String idempotencyKey, String displayName) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", idempotencyKey);
        var response = http.postForEntity(
                url("/api/v1/model-providers"),
                new HttpEntity<>(
                        Map.of(
                                "providerKind", "DETERMINISTIC",
                                "displayName", displayName,
                                "endpoint", "memory://deterministic",
                                "region", "本地",
                                "authenticationType", "API_KEY",
                                "credentialReference", "secret://demo/provider"),
                        headers),
                ProviderResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private ProviderPage list(String token) {
        var response = http.exchange(
                url("/api/v1/model-providers"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                ProviderPage.class);
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

    private record ProviderResponse(
            UUID id,
            String providerKind,
            String displayName,
            String endpoint,
            String region,
            String authenticationType,
            boolean credentialConfigured,
            String status,
            long version,
            Boolean lastProbeSucceeded,
            String lastProbeCode,
            Instant lastProbedAt,
            Instant createdAt) {
    }

    private record ProviderPage(List<ProviderResponse> items, String nextCursor) {
    }

    private record ProbeResponse(
            ProviderResponse providerConnection,
            boolean reachable,
            List<String> capabilities,
            String detailCode,
            Instant probedAt) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
