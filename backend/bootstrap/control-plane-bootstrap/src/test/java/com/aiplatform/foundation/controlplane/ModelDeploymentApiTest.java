package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.redis.testcontainers.RedisContainer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
class ModelDeploymentApiTest {

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
    private ModelRuntimeResolver modelRuntimeResolver;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @BeforeEach
    void clearModelCatalog() {
        jdbc.update("DELETE FROM model_runtime.model_deployment_request");
        jdbc.update("DELETE FROM model_runtime.model_deployment");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
    }

    @Test
    void 租户管理员可创建探测发布并列出冻结修订的模型部署() {
        assertNotNull(modelRuntimeResolver);
        var storeToken = login("demo-store");
        var campusToken = login("demo-campus");
        var provider = createVerifiedProvider(storeToken);
        var created = createDeployment(storeToken, provider.id(), "deployment-http-0001");
        var replayed = createDeployment(storeToken, provider.id(), "deployment-http-0001");

        assertEquals(created.id(), replayed.id());
        assertEquals(provider.version(), created.providerConnectionRevision());
        assertEquals("DRAFT", created.status());

        var probe = http.postForEntity(
                url("/api/v1/model-deployments/" + created.id() + "/probe"),
                new HttpEntity<>(
                        Map.of("expectedVersion", 0, "timeoutMillis", 2000),
                        authorized(storeToken)),
                DeploymentProbeResponse.class);
        assertEquals(HttpStatus.OK, probe.getStatusCode());
        assertNotNull(probe.getBody());
        assertTrue(probe.getBody().reachable());
        assertEquals("VERIFIED", probe.getBody().modelDeployment().status());

        var published = http.postForEntity(
                url("/api/v1/model-deployments/" + created.id() + "/publish"),
                new HttpEntity<>(
                        Map.of("expectedVersion", 1),
                        authorized(storeToken)),
                DeploymentResponse.class);
        assertEquals(HttpStatus.OK, published.getStatusCode());
        assertNotNull(published.getBody());
        assertEquals("PUBLISHED", published.getBody().status());
        assertEquals(2, published.getBody().version());

        var store = list(storeToken);
        var campus = list(campusToken);
        assertEquals(List.of(created.id()), store.items().stream()
                .map(DeploymentResponse::id)
                .toList());
        assertEquals(1, store.totalElements());
        assertEquals(1, store.totalPages());
        assertFalse(store.hasPrevious());
        assertFalse(store.hasNext());
        assertTrue(campus.items().isEmpty());
        assertEquals(0, campus.totalElements());
    }

    @Test
    void 未登录不能读取模型部署() {
        var response = http.getForEntity(
                url("/api/v1/model-deployments"),
                ApiError.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PLATFORM_SECURITY.AUTHENTICATION.0001", response.getBody().code());
    }

    private ProviderResponse createVerifiedProvider(String token) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", "provider-for-deployment-http");
        var created = http.postForEntity(
                url("/api/v1/model-providers"),
                new HttpEntity<>(
                        Map.of(
                                "providerKind", "DETERMINISTIC",
                                "displayName", "模型部署验收供应商",
                                "endpoint", "memory://deterministic",
                                "region", "本地",
                                "authenticationType", "API_KEY",
                                "credentialReference", "secret://demo/provider"),
                        headers),
                ProviderResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        var probe = http.postForEntity(
                url("/api/v1/model-providers/" + created.getBody().id() + "/probe"),
                new HttpEntity<>(
                        Map.of(
                                "expectedVersion", 0,
                                "modelCode", "deterministic-chat",
                                "timeoutMillis", 2000),
                        authorized(token)),
                ProviderProbeResponse.class);
        assertEquals(HttpStatus.OK, probe.getStatusCode());
        assertNotNull(probe.getBody());
        return probe.getBody().providerConnection();
    }

    private DeploymentResponse createDeployment(
            String token,
            String providerId,
            String idempotencyKey) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", idempotencyKey);
        var response = http.postForEntity(
                url("/api/v1/model-deployments"),
                new HttpEntity<>(
                        Map.of(
                                "providerConnectionId", providerId,
                                "modelCode", "deterministic-chat",
                                "displayName", "确定性对话部署",
                                "modelType", "CHAT",
                                "capabilities", List.of("CHAT"),
                                "contextWindow", 8192,
                                "outputLimit", 1024),
                        headers),
                DeploymentResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private DeploymentPage list(String token) {
        var response = http.exchange(
                url("/api/v1/model-deployments"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                DeploymentPage.class);
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

    private record ProviderResponse(String id, long version) {
    }

    private record ProviderProbeResponse(ProviderResponse providerConnection) {
    }

    private record DeploymentResponse(
            String id,
            String providerConnectionId,
            long providerConnectionRevision,
            String status,
            long version) {
    }

    private record DeploymentProbeResponse(
            DeploymentResponse modelDeployment,
            boolean reachable,
            List<String> capabilities,
            String detailCode) {
    }

    private record DeploymentPage(
            List<DeploymentResponse> items,
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
