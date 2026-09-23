package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.redis.testcontainers.RedisContainer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "foundation.model-credentials.local-development-key-version=M2_V1")
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
@Import(AssistantApiTest.TestSecrets.class)
class AssistantApiTest {

    private static final long STORE =
            100000000000000001L;
    private static final long CREATOR = 3382738893456539650L;
    private static final long CHAT = 320000000000000001L;
    private static final long CHAT_VERIFICATION = 330000000000000001L;
    private static final long SPACE = 210000000000000001L;
    private static final long INDEX = 230000000000000001L;
    private static final long EMBEDDING = 320000000000000002L;
    private static final long CHAT_CATALOG = 330000000000000001L;
    private static final long EMBEDDING_CATALOG = 330000000000000002L;
    private static final long CHAT_CONFIGURATION = 340000000000000001L;
    private static final long EMBEDDING_CONFIGURATION = 340000000000000002L;
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
        jdbc.update("DELETE FROM assistant_delivery.assistant_version_chat_route_candidate");
        jdbc.update("DELETE FROM assistant_delivery.assistant_version");
        jdbc.update("DELETE FROM assistant_delivery.assistant");
        jdbc.update("DELETE FROM model_runtime.model_deployment_request");
        jdbc.update("""
                UPDATE model_runtime.model_deployment
                SET status = 'DRAFT', last_verification_id = NULL, verification_expires_at = NULL
                WHERE last_verification_id IS NOT NULL
                """);
        jdbc.update("DELETE FROM model_runtime.model_capability_verification");
        jdbc.update("DELETE FROM model_runtime.model_deployment");
        jdbc.update("DELETE FROM model_runtime.provider_connection_request");
        jdbc.update("DELETE FROM model_runtime.provider_connection");
        jdbc.update("DELETE FROM knowledge_data.index_build_job");
        jdbc.update("DELETE FROM knowledge_data.index_version_source");
        jdbc.update("DELETE FROM knowledge_data.index_version_request");
        jdbc.update("DELETE FROM knowledge_data.index_version");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
        jdbc.update("DELETE FROM model_runtime.model_configuration_credential");
        jdbc.update("DELETE FROM model_runtime.model_configuration");
        jdbc.update("DELETE FROM model_runtime.controlled_model_catalog_entry");
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
        var versionRequest = new HttpEntity<>(
                Map.of(
                        "chatModelConfigurationId", Long.toString(CHAT_CONFIGURATION),
                        "embeddingModelConfigurationId", Long.toString(EMBEDDING_CONFIGURATION),
                        "knowledgeSpaceId", Long.toString(SPACE),
                        "systemInstruction", "只依据编号证据回答，并在结论后标注引用。",
                        "maxOutputTokens", 512,
                        "retrievalLimit", 5,
                        "minimumScore", 0.2,
                        "citationPolicy", "REQUIRED",
                        "refusalPolicy", "INSUFFICIENT_EVIDENCE"),
                versionHeaders);
        var version = http.postForEntity(
                url("/api/v1/assistants/" + created.id() + "/versions"),
                versionRequest,
                AssistantVersionResponse.class);
        assertEquals(HttpStatus.CREATED, version.getStatusCode());
        assertNotNull(version.getBody());
        assertEquals(Long.toString(CHAT_CONFIGURATION), version.getBody().chatModelConfigurationId());
        assertEquals(1L, version.getBody().chatModelConfigurationRevision());
        assertEquals(4, version.getBody().indexVersionRevision());
        assertEquals(Long.toString(EMBEDDING_CONFIGURATION), version.getBody().embeddingModelConfigurationId());
        assertEquals(1L, version.getBody().embeddingModelConfigurationRevision());
        assertEquals("DRAFT", version.getBody().status());
        var published = http.postForEntity(
                url("/api/v1/assistant-versions/" + version.getBody().id() + "/publish"),
                new HttpEntity<>(
                        Map.of(
                                "expectedVersion", version.getBody().version(),
                                "releaseDecisionId", 360000000000000001L),
                        withIdempotency(
                                storeToken,
                                "assistant-publish-http-0001")),
                ApiError.class);
        assertEquals(HttpStatus.BAD_REQUEST, published.getStatusCode());
        assertNotNull(published.getBody());
        assertEquals("COMMON.REQUEST.0001", published.getBody().code());
        assertTrue(publishedVersions.find(
                        com.aiplatform.foundation.shared.kernel.tenant.TenantId.parse(Long.toString(STORE)),
                        Long.parseLong(version.getBody().id()))
                .isEmpty());

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
        assertEquals("PLATFORM_SECURITY.AUTHENTICATION.0001", response.getBody().code());
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
                        "loginName", "demo-campus".equals(tenantCode) ? "tenant-admin-candidate" : "admin",
                        "password", "Demo@2026!"),
                SessionResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().tokenValue();
    }

    private void seedReferences() {
        var provider = 310000000000000001L;
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
                    'CHAT', ARRAY['CHAT'], 8192, 1024, NULL, 'DRAFT', 9, ?)
                """,
                STORE,
                CHAT,
                provider,
                Timestamp.from(NOW.minusSeconds(30)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_capability_verification (
                    id, tenant_id, model_deployment_id, model_deployment_version,
                    provider_connection_id, provider_connection_revision, model_code,
                    declared_capabilities, observed_capabilities, succeeded, result_code,
                    elapsed_millis, probed_at, expires_at
                ) VALUES (?, ?, ?, 9, ?, 1, 'deterministic-chat',
                    ARRAY['CHAT'], ARRAY['CHAT'], TRUE, 'DETERMINISTIC_OK',
                    1, ?, ?)
                """,
                CHAT_VERIFICATION,
                STORE,
                CHAT,
                provider,
                Timestamp.from(NOW.minusSeconds(20)),
                Timestamp.from(NOW.plusSeconds(3600)));
        jdbc.update(
                """
                UPDATE model_runtime.model_deployment
                SET status = 'PUBLISHED', last_verification_id = ?, verification_expires_at = ?
                WHERE tenant_id = ? AND id = ?
                """,
                CHAT_VERIFICATION,
                Timestamp.from(NOW.plusSeconds(3600)),
                STORE,
                CHAT);
        jdbc.update(
                """
                INSERT INTO model_runtime.controlled_model_catalog_entry (
                    id, provider_kind, endpoint, model_code, model_type, status, version,
                    created_at, context_window, output_limit, embedding_dimension
                ) VALUES (?, 'OPENAI_COMPATIBLE', 'https://models.example.test/v1',
                    'assistant-test-chat', 'CHAT', 'PUBLISHED', 1, ?, 8192, 1024, NULL),
                   (?, 'OPENAI_COMPATIBLE', 'https://models.example.test/v1',
                    'assistant-test-embedding', 'EMBEDDING', 'PUBLISHED', 1, ?, 8192, NULL, 8)
                """,
                CHAT_CATALOG,
                Timestamp.from(NOW.minusSeconds(30)),
                EMBEDDING_CATALOG,
                Timestamp.from(NOW.minusSeconds(30)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_configuration (
                    id, scope, tenant_id, owner_membership_id, display_name, catalog_entry_id,
                    status, version, created_at
                ) VALUES (?, 'TENANT', ?, NULL, '助手测试对话模型', ?, 'ACTIVE', 1, ?),
                   (?, 'TENANT', ?, NULL, '助手测试嵌入模型', ?, 'ACTIVE', 1, ?)
                """,
                CHAT_CONFIGURATION,
                STORE,
                CHAT_CATALOG,
                Timestamp.from(NOW.minusSeconds(30)),
                EMBEDDING_CONFIGURATION,
                STORE,
                EMBEDDING_CATALOG,
                Timestamp.from(NOW.minusSeconds(30)));
        jdbc.update(
                """
                INSERT INTO model_runtime.model_configuration_credential (
                    model_configuration_id, algorithm, master_key_version, data_key_nonce,
                    encrypted_data_key, ciphertext_nonce, ciphertext, fingerprint, rotated_at
                ) VALUES (?, 'AES-256-GCM', 'M2_V1', ?, ?, ?, ?, ?, ?),
                   (?, 'AES-256-GCM', 'M2_V1', ?, ?, ?, ?, ?, ?)
                """,
                CHAT_CONFIGURATION,
                new byte[12],
                new byte[] {1},
                new byte[12],
                new byte[] {1},
                "a".repeat(64),
                Timestamp.from(NOW.minusSeconds(30)),
                EMBEDDING_CONFIGURATION,
                new byte[12],
                new byte[] {2},
                new byte[12],
                new byte[] {2},
                "b".repeat(64),
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
                    embedding_configuration_id, embedding_configuration_revision,
                    embedding_catalog_entry_id, embedding_catalog_entry_revision,
                    embedding_dimension, status, version, build_digest, chunk_count,
                    failure_code, created_by, created_at, ready_at, activated_at,
                    superseded_at
                ) VALUES (?, ?, ?, ?, 'fixed-800-120', NULL, NULL, ?, 1, ?, 1, 8, 'ACTIVE', 4, ?,
                    2, NULL, ?, ?, ?, ?, NULL)
                """,
                STORE,
                SPACE,
                INDEX,
                "a".repeat(64),
                EMBEDDING_CONFIGURATION,
                EMBEDDING_CATALOG,
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

    private record AssistantResponse(String id, int latestVersionNumber, long version) {
    }

    private record AssistantVersionResponse(
            String id,
            String chatModelConfigurationId,
            Long chatModelConfigurationRevision,
            long indexVersionRevision,
            String embeddingModelConfigurationId,
            Long embeddingModelConfigurationRevision,
            String status,
            long version) {
    }

    private record AssistantPage(
            List<AssistantResponse> items,
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

    /**
     * 仅为隔离集成测试提供固定的 Base64 主密钥，不能用于本地或生产运行。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecrets {

        private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

        @Bean
        SecretEncryptionKeyProvider secretEncryptionKeyProvider() {
            return new SecretEncryptionKeyProvider() {
                @Override
                public ActiveKey activeKey() {
                    return new ActiveKey("M2_V1", secret());
                }

                @Override
                public SecretValue resolve(String version) {
                    if (!"M2_V1".equals(version)) {
                        throw new IllegalArgumentException("测试主密钥版本不可用");
                    }
                    return secret();
                }

                private SecretValue secret() {
                    return SecretValue.copyOf(TEST_KEY.toCharArray());
                }
            };
        }
    }
}
