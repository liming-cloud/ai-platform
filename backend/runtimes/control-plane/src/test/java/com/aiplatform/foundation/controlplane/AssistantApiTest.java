package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.api.PublishedAssistantVersionLookup;
import com.redis.testcontainers.RedisContainer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class AssistantApiTest {

    private static final long STORE =
            100000000000000001L;
    private static final long CREATOR = 3382738893456539650L;
    private static final UUID CHAT =
            UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID SPACE =
            UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID INDEX =
            UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID EMBEDDING =
            UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T07:00:00Z");

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
    private PublishedAssistantVersionLookup publishedVersions;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @BeforeEach
    void clearAndSeedReferences() {
        jdbc.update("DELETE FROM assistant_delivery.assistant_request");
        jdbc.update("DELETE FROM assistant_delivery.assistant_version");
        jdbc.update("DELETE FROM assistant_delivery.assistant");
        jdbc.update("DELETE FROM model_runtime.model_deployment_request");
        jdbc.update("DELETE FROM model_runtime.model_deployment");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        jdbc.update("DELETE FROM knowledge_data.index_build_job");
        jdbc.update("DELETE FROM knowledge_data.index_version_source");
        jdbc.update("DELETE FROM knowledge_data.index_version_request");
        jdbc.update("DELETE FROM knowledge_data.index_version");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        seedReferences();
    }

    @Test
    void 租户管理员可创建助手冻结引用发布并隔离列表() {
        assertNotNull(publishedVersions);
        var storeToken = login("demo-store");
        var campusToken = login("demo-campus");

        var created = createAssistant(storeToken, "assistant-http-0001");
        var replayed = createAssistant(storeToken, "assistant-http-0001");
        assertEquals(created.id(), replayed.id());

        var versionHeaders = authorized(storeToken);
        versionHeaders.add("Idempotency-Key", "assistant-version-http-0001");
        var version = http.postForEntity(
                url("/api/v1/assistants/" + created.id() + "/versions"),
                new HttpEntity<>(
                        Map.of(
                                "chatDeploymentId", CHAT,
                                "knowledgeSpaceId", SPACE,
                                "systemInstruction", "只依据编号证据回答，并在结论后标注引用。",
                                "maxOutputTokens", 512,
                                "retrievalLimit", 5,
                                "minimumScore", 0.2,
                                "citationPolicy", "REQUIRED",
                                "refusalPolicy", "INSUFFICIENT_EVIDENCE"),
                        versionHeaders),
                AssistantVersionResponse.class);
        assertEquals(HttpStatus.CREATED, version.getStatusCode());
        assertNotNull(version.getBody());
        assertEquals(9, version.getBody().chatDeploymentRevision());
        assertEquals(4, version.getBody().indexVersionRevision());
        assertEquals(3, version.getBody().embeddingDeploymentRevision());
        assertEquals("DRAFT", version.getBody().status());

        var published = http.postForEntity(
                url("/api/v1/assistant-versions/" + version.getBody().id() + "/publish"),
                new HttpEntity<>(
                        Map.of("expectedVersion", 0),
                        withIdempotency(
                                storeToken,
                                "assistant-publish-http-0001")),
                AssistantVersionResponse.class);
        assertEquals(HttpStatus.OK, published.getStatusCode());
        assertNotNull(published.getBody());
        assertEquals("PUBLISHED", published.getBody().status());
        assertEquals(1, published.getBody().version());
        var publishReplay = http.postForEntity(
                url("/api/v1/assistant-versions/" + version.getBody().id() + "/publish"),
                new HttpEntity<>(
                        Map.of("expectedVersion", 0),
                        withIdempotency(
                                storeToken,
                                "assistant-publish-http-0001")),
                AssistantVersionResponse.class);
        assertEquals(HttpStatus.OK, publishReplay.getStatusCode());
        assertEquals(
                "true",
                publishReplay.getHeaders().getFirst("Idempotency-Replayed"));
        assertNotNull(publishReplay.getBody());
        assertEquals(published.getBody().id(), publishReplay.getBody().id());
        assertTrue(publishedVersions.find(
                        com.aiplatform.foundation.kernel.tenant.TenantId.parse(
                                Long.toString(STORE)),
                        published.getBody().id())
                .isPresent());

        assertEquals(List.of(created.id()), list(storeToken).items().stream()
                .map(AssistantResponse::id)
                .toList());
        assertTrue(list(campusToken).items().isEmpty());
    }

    @Test
    void 未登录不能读取助手列表() {
        var response = http.getForEntity(url("/api/v1/assistants"), ApiError.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTHENTICATION_REQUIRED", response.getBody().code());
    }

    private AssistantResponse createAssistant(String token, String idempotencyKey) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", idempotencyKey);
        var response = http.postForEntity(
                url("/api/v1/assistants"),
                new HttpEntity<>(
                        Map.of("displayName", "门店导购助手"),
                        headers),
                AssistantResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private AssistantPage list(String token) {
        var response = http.exchange(
                url("/api/v1/assistants"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                AssistantPage.class);
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

    private void seedReferences() {
        var provider = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO model_runtime.provider_connection (
                    tenant_id, id, provider_kind, display_name, endpoint, region,
                    authentication_type, credential_reference, status, version,
                    last_probe_succeeded, last_probe_code, last_probed_at, created_at
                ) VALUES (?, ?, 'DETERMINISTIC', '助手测试供应商',
                    'memory://deterministic', '本地', 'API_KEY',
                    'secret://test/assistant-provider', 'VERIFIED', 1, TRUE,
                    'DETERMINISTIC_OK', ?, ?)
                """,
                STORE,
                provider,
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(120)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_deployment (
                    tenant_id, id, provider_connection_id, provider_connection_revision,
                    model_code, display_name, model_type, capabilities, context_window,
                    output_limit, embedding_dimension, status, version, created_at
                ) VALUES (?, ?, ?, 1, 'deterministic-chat', '助手测试对话部署',
                    'CHAT', ARRAY['CHAT'], 8192, 1024, NULL, 'PUBLISHED', 9, ?)
                """,
                STORE,
                CHAT,
                provider,
                Timestamp.from(NOW.minusSeconds(30)));
        jdbc.update(
                """
                INSERT INTO knowledge_data.knowledge_space (
                    tenant_id, id, name, description, status, version, created_by, created_at
                ) VALUES (?, ?, '助手测试知识空间', NULL, 'DRAFT', 0, ?, ?)
                """,
                STORE,
                SPACE,
                CREATOR,
                Timestamp.from(NOW.minusSeconds(120)));
        jdbc.update(
                """
                INSERT INTO knowledge_data.index_version (
                    tenant_id, knowledge_space_id, id, source_snapshot_digest, chunk_policy,
                    embedding_deployment_id, embedding_deployment_revision,
                    embedding_dimension, status, version, build_digest, chunk_count,
                    failure_code, created_by, created_at, ready_at, activated_at,
                    superseded_at
                ) VALUES (?, ?, ?, ?, 'fixed-800-120', ?, 3, 8, 'ACTIVE', 4, ?,
                    2, NULL, ?, ?, ?, ?, NULL)
                """,
                STORE,
                SPACE,
                INDEX,
                "a".repeat(64),
                EMBEDDING,
                "b".repeat(64),
                CREATOR,
                Timestamp.from(NOW.minusSeconds(90)),
                Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(30)));
    }

    private static HttpHeaders authorized(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpHeaders withIdempotency(String token, String key) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", key);
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private record SessionResponse(String tokenValue) {
    }

    private record AssistantResponse(UUID id, int latestVersionNumber, long version) {
    }

    private record AssistantVersionResponse(
            UUID id,
            long chatDeploymentRevision,
            long indexVersionRevision,
            long embeddingDeploymentRevision,
            String status,
            long version) {
    }

    private record AssistantPage(List<AssistantResponse> items, String nextCursor) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
