package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.*;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateSource;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.redis.testcontainers.RedisContainer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S2 的正式 HTTP 装配回归：只替换外部厂商传输，保留真实会话、加密、SDK、数据库和事务。
 * 合成来源与报价仅属于隔离容器，不证明真实账户费率、模型能力或正式发布已验收。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "foundation.model-credentials.local-development-key-version=M2_V1")
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({AssistantApiTest.TestSecrets.class, PlatformSupplyWorkflowApiTest.LocalVendorOnly.class})
class PlatformSupplyWorkflowApiTest {
    private static final String ROOT = "/api/v1/platform/model-supply/deployments";
    private static final String STORE = "100000000000000001";
    private static final String CATALOG = "/api/v1/controlled-model-catalog";
    private static final long ACTOR = 4921825928971026436L;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<String> FAILURE = new AtomicReference<>("");
    private static final SSLContext ORIGINAL_TLS = originalTls();
    private static final String ORIGINAL_TRUST_STORE = System.getProperty("javax.net.ssl.trustStore");
    private static final String ORIGINAL_TRUST_TYPE = System.getProperty("javax.net.ssl.trustStoreType");
    private static final String ORIGINAL_TRUST_PASSWORD = System.getProperty("javax.net.ssl.trustStorePassword");
    private static Path tlsDirectory;
    private static final java.util.concurrent.ExecutorService VENDOR_THREADS = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer VENDOR = vendor();
    private static final URI ENDPOINT = URI.create("https://127.0.0.1:" + VENDOR.getAddress().getPort() + "/v1");

    @Container private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("pgvector/pgvector:0.8.5-pg18-trixie")
                    .withDatabaseName("foundation_test").withUsername("foundation").withPassword("foundation_test_only");
    @Container private static final RedisContainer REDIS = new RedisContainer("redis:8.8.1-trixie");
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformProviderConnectionCredentialWriter credentials;
    private String platform;
    private String store;
    private String campus;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
        for (var type : Type.values()) {
            var prefix = "foundation.model-catalog.platform-verification-prices[" + type.ordinal() + "].";
            var price = Map.<String, Object>ofEntries(
                    Map.entry("connection-id", type.connection()), Map.entry("connection-revision", 1),
                    Map.entry("model-code", type.code()), Map.entry("model-type", type.name()),
                    Map.entry("residency", "synthetic-local"), Map.entry("evidence-digest", "c".repeat(64)),
                    Map.entry("observed-at", Instant.now().minusSeconds(60).toString()),
                    Map.entry("expires-at", Instant.now().plusSeconds(3600).toString()),
                    Map.entry("currency", "CNY"),
                    Map.entry("billing-unit", type == Type.RERANK ? "PER_REQUEST" : "PER_MILLION_TOKENS"),
                    Map.entry("input-price", type == Type.RERANK ? "0.001" : "1"),
                    Map.entry("output-price", type == Type.CHAT ? "1" : "0"),
                    Map.entry("input-units-upper-bound", type == Type.RERANK ? 1 : 64),
                    Map.entry("input-tokens-upper-bound", 64));
            price.forEach((key, value) -> registry.add(prefix + key, () -> value));
        }
    }

    @BeforeEach
    void prepareSyntheticSources() {
        CALLS.set(0);
        FAILURE.set("");
        platform = login(true, null);
        store = login(false, "demo-store");
        campus = login(false, "demo-campus");
        for (var type : Type.values()) seedSource(type);
    }

    @AfterAll
    static void stopVendor() throws IOException {
        try {
            VENDOR.stop(0);
            VENDOR_THREADS.shutdownNow();
            SSLContext.setDefault(ORIGINAL_TLS);
        } finally {
            restoreProperty("javax.net.ssl.trustStore", ORIGINAL_TRUST_STORE);
            restoreProperty("javax.net.ssl.trustStoreType", ORIGINAL_TRUST_TYPE);
            restoreProperty("javax.net.ssl.trustStorePassword", ORIGINAL_TRUST_PASSWORD);
            Files.deleteIfExists(tlsDirectory.resolve("synthetic.p12"));
            Files.deleteIfExists(tlsDirectory);
        }
    }

    @Test
    void 部署目录搜索沿用平台权限分页和安全公开字段且零外呼() {
        var term = "Search" + UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 2; i++) {
            var body = draft(Type.CHAT, Type.CHAT.code());
            body.put("displayName", term + i);
            create(platform, body, UUID.randomUUID().toString());
        }
        var first = get(ROOT + "?q=" + term.toLowerCase(java.util.Locale.ROOT) + "&pageSize=1", platform);
        var second = get(ROOT + "?q=" + term + "&pageNumber=2&pageSize=1", platform);
        assertEquals(2L, first.path("totalElements").asLong());
        assertEquals(2L, second.path("totalElements").asLong());
        assertEquals(1, first.path("items").size()); assertEquals(1, second.path("items").size());
        assertNotEquals(first.path("items").get(0).path("id"), second.path("items").get(0).path("id"));
        assertFalse(first.toString().contains("credentialReference"));
        assertFalse(first.toString().contains("endpoint"));
        assertEquals(0, get(ROOT + "?q=" + term + "nomatch", platform).path("items").size());
        var headers = new HttpHeaders(); headers.setBearerAuth(platform);
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange(ROOT + "?q=" + "x".repeat(65), HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange(ROOT + "?pageNumber=10002&pageSize=10", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.getForEntity(ROOT + "?q=" + term, JsonNode.class).getStatusCode());
        headers.setBearerAuth(store);
        assertEquals(HttpStatus.UNAUTHORIZED, http.exchange(ROOT + "?q=" + term, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class).getStatusCode());
        assertEquals(0, CALLS.get());
    }

    @ParameterizedTest
    @EnumSource(Type.class)
    void 三类协议经正式接口完成验证发布租户可见性与撤权(Type type) {
        var request = draft(type, type.code());
        var key = UUID.randomUUID().toString();
        var created = create(platform, request, key);
        var id = created.path("id").asString();
        assertEquals("DRAFT", created.path("status").asString());
        assertEquals(0, created.path("version").asLong());
        assertEquals(created, create(platform, request, key));
        assertEquals(1, count("platform_model_deployment", "id", id));
        assertEquals(0, count("platform_model_deployment_publication", "deployment_id", id));
        assertEquals(0, CALLS.get());

        var verified = post(ROOT + "/" + id + "/probe", platform,
                Map.of("expectedVersion", 0, "timeoutMillis", 30000), HttpStatus.OK);
        assertTrue(verified.path("verified").asBoolean(), verified.path("resultCode").asString());
        assertEquals("VERIFIED", verified.path("deployment").path("status").asString());
        assertEquals(1, verified.path("deployment").path("version").asLong());
        assertEquals(3, CALLS.get());
        assertEquals(1, count("platform_model_capability_verification", "platform_model_deployment_id", id));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM model_runtime.platform_capability_verification_reservation r
                JOIN model_runtime.platform_model_deployment_verification_audit a ON a.reservation_id = r.id
                WHERE r.deployment_id = ? AND r.status = 'COMPLETED' AND r.outcome = 'PASSED'
                  AND r.attempted_requests = 3 AND cardinality(r.passed_sample_digests) = 3
                  AND r.passed_sample_digests = r.expected_sample_digests
                  AND r.reserved_fee > 0 AND r.reserved_fee <= 0.03 AND a.succeeded = TRUE
                """, Long.class, Long.parseLong(id)));
        assertEquals(0, count("platform_model_deployment_publication", "deployment_id", id));

        var published = post(ROOT + "/" + id + "/publish", platform, Map.of("expectedVersion", 1), HttpStatus.OK);
        assertEquals("PUBLISHED", published.path("status").asString());
        assertEquals(2, published.path("version").asLong());
        var facts = get(ROOT + "/" + id + "/supply", platform);
        var entryId = facts.path("catalogEntryId").asString();
        assertEquals(1, facts.path("selectedTenantCount").asLong());
        assertEquals(1, facts.path("activeGrantCount").asLong());
        assertEquals(0, facts.path("revokedGrantCount").asLong());
        assertTrue(facts.path("currentSourceAvailable").asBoolean());
        assertTrue(visible(store, entryId));
        assertFalse(visible(campus, entryId));
        assertEquals(1, count("platform_model_deployment_publication", "deployment_id", id));

        // 旧修订重试不能追加发布／授权，租户也不能冒充平台发布。
        var stale = exchange(ROOT + "/" + id + "/publish", platform, Map.of("expectedVersion", 1), null);
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertNotNull(stale.getBody());
        assertEquals("MODEL_RUNTIME.CONFLICT.0004", stale.getBody().path("code").asString());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange(ROOT + "/" + id + "/publish", campus,
                Map.of("expectedVersion", 2), null).getStatusCode());
        assertEquals(1, count("platform_model_deployment_publication", "deployment_id", id));
        assertEquals(3, CALLS.get());

        var revoke = ROOT + "/" + id + "/grants/" + STORE + "/revoke";
        post(revoke, platform, Map.of("expectedVersion", 2, "expectedGrantVersion", 0), HttpStatus.NO_CONTENT);
        assertFalse(visible(store, entryId));
        facts = get(ROOT + "/" + id + "/supply", platform);
        assertEquals(0, facts.path("activeGrantCount").asLong());
        assertEquals(1, facts.path("revokedGrantCount").asLong());
        assertEquals(HttpStatus.CONFLICT, exchange(revoke, platform,
                Map.of("expectedVersion", 2, "expectedGrantVersion", 0), null).getStatusCode());
        var disabled = post(ROOT + "/" + id + "/disable", platform, Map.of("expectedVersion", 2), HttpStatus.OK);
        assertEquals("DISABLED", disabled.path("status").asString());
        assertEquals(1, count("platform_model_capability_verification", "platform_model_deployment_id", id));
        assertEquals(3, CALLS.get());
    }

    @Test
    void 缺报价和租户越权均不能产生厂商调用或发布() {
        var request = draft(Type.CHAT, "synthetic-unpriced");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange(ROOT, campus, request, UUID.randomUUID().toString()).getStatusCode());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM model_runtime.platform_model_deployment WHERE display_name = ?",
                Long.class, request.get("displayName")));
        var created = create(platform, request, UUID.randomUUID().toString());
        var id = created.path("id").asString();
        var result = post(ROOT + "/" + id + "/probe", platform,
                Map.of("expectedVersion", 0, "timeoutMillis", 30000), HttpStatus.OK);
        assertFalse(result.path("verified").asBoolean());
        assertEquals("DRAFT", result.path("deployment").path("status").asString());
        assertEquals(0, CALLS.get());
        assertEquals(0, count("platform_capability_verification_reservation", "deployment_id", id));
        assertCannotPublish(id, result.path("deployment").path("version").asLong());
    }

    @Test
    void 真实SDK返回错误维度时不能留下通过验证和供应事实() {
        FAILURE.set("dimension");
        var created = create(platform, draft(Type.EMBEDDING, Type.EMBEDDING.code()), UUID.randomUUID().toString());
        var id = created.path("id").asString();
        var result = post(ROOT + "/" + id + "/probe", platform,
                Map.of("expectedVersion", 0, "timeoutMillis", 30000), HttpStatus.OK);
        assertFalse(result.path("verified").asBoolean());
        assertEquals("DRAFT", result.path("deployment").path("status").asString());
        assertEquals(1, CALLS.get());
        assertCannotPublish(id, result.path("deployment").path("version").asLong());
    }

    private void assertCannotPublish(String id, long version) {
        var response = exchange(ROOT + "/" + id + "/publish", platform, Map.of("expectedVersion", version), null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(), "未验证部署应返回可识别的业务冲突");
        assertNotNull(response.getBody());
        assertEquals("MODEL_RUNTIME.CONFLICT.0008", response.getBody().path("code").asString());
        assertEquals(0, count("platform_model_deployment_publication", "deployment_id", id));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM model_runtime.platform_model_supply_grant g
                JOIN model_runtime.platform_model_deployment_publication p ON p.id = g.publication_id
                WHERE p.deployment_id = ?
                """, Long.class, Long.parseLong(id)));
    }

    /** 只准备 S1 已交付的合成前置事实；S2 的部署、验证、发布与授权全部由正式接口产生。 */
    private void seedSource(Type type) {
        var now = Timestamp.from(Instant.now().minusSeconds(5));
        var expires = Timestamp.from(Instant.now().plusSeconds(3600));
        var profile = type == Type.RERANK ? "SILICONFLOW_RERANK_MODELS_API" : "OPENAI_MODELS_API";
        jdbc.update("""
                INSERT INTO model_runtime.platform_provider_connection
                (id, scope, provider_kind, display_name, endpoint, region, authentication_type, credential_reference,
                 status, version, last_probe_succeeded, last_probe_code, last_probed_at, created_at, discovery_profile,
                 provider_preset_id, provider_preset_version, capability_intent)
                VALUES (?, 'PLATFORM', 'OPENAI_COMPATIBLE', ?, ?, 'synthetic-local', 'API_KEY', ?,
                        'VERIFIED', 1, TRUE, 'SYNTHETIC_OK', ?, ?, ?, ?, '2026-10-20', ?)
                ON CONFLICT (id) DO NOTHING
                """, type.connection(), "隔离合成来源" + type, type.endpoint().toString(),
                "secret://platform-provider-connection/" + type.connection(), now, now, profile,
                type == Type.CHAT ? "VOLCENGINE" : "SILICONFLOW", type.name());
        jdbc.update("INSERT INTO model_runtime.platform_provider_model_discovery VALUES (?, ?, 1, TRUE, 'SYNTHETIC_OK', 0, ?, ?) ON CONFLICT (id) DO NOTHING",
                type.connection() + 100, type.connection(), now, expires);
        jdbc.update("INSERT INTO model_runtime.platform_discovered_model (discovery_id, model_code, model_type, capabilities, classification, display_name) VALUES (?, ?, ?, ARRAY[?], 'CONFIRMED_BY_PROVIDER', ?) ON CONFLICT DO NOTHING",
                type.connection() + 100, type.code(), type.name(), type.name(), "隔离合成模型" + type);
        if (type == Type.RERANK) jdbc.update("""
                UPDATE model_runtime.platform_discovered_model SET source_profile = ?, list_source = ?, mapping_version = ?, mapping_digest = ?
                WHERE discovery_id = ?
                """, profile, "https://api.siliconflow.cn/v1/models?sub_type=reranker",
                PlatformModelCandidateSource.CURRENT_MAPPING_VERSION, PlatformModelCandidateSource.CURRENT_MAPPING_DIGEST, type.connection() + 100);
        if (type == Type.CHAT) jdbc.update("INSERT INTO model_runtime.platform_discovered_model (discovery_id, model_code, model_type, capabilities, classification, display_name) VALUES (?, 'synthetic-unpriced', 'CHAT', ARRAY['CHAT'], 'CONFIRMED_BY_PROVIDER', '隔离无报价模型') ON CONFLICT DO NOTHING", type.connection() + 100);
        jdbc.update("INSERT INTO model_runtime.platform_provider_connection_publication VALUES (?, ?, 1, 1, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                type.connection() + 200, type.connection(), ACTOR, now, now);
        try (var secret = SecretValue.copyOf(UUID.randomUUID().toString().toCharArray())) {
            credentials.write(new CredentialReference("secret://platform-provider-connection/" + type.connection()), secret);
        }
    }

    private Map<String, Object> draft(Type type, String code) {
        var body = new LinkedHashMap<String, Object>();
        body.put("providerConnectionId", Long.toString(type.connection()));
        body.put("expectedConnectionVersion", 1);
        body.put("modelCode", code);
        body.put("displayName", "隔离流程" + type + UUID.randomUUID());
        body.put("modelType", type.name());
        body.put("capabilities", List.of(type.name()));
        body.put("contextWindow", 128);
        if (type == Type.CHAT) body.put("outputLimit", 64);
        if (type == Type.EMBEDDING) body.put("embeddingDimension", 1024);
        if (type == Type.RERANK) body.put("rerankCandidateLimit", 3);
        body.put("parameterSource", "隔离合成合同，不代表真实厂商声明");
        body.put("plannedTenantIds", List.of(STORE));
        body.put("modelConfirmed", true);
        return body;
    }

    private long count(String table, String column, String id) {
        // 表和列只来自本测试的固定字面量，业务标识仍使用绑定参数。
        return jdbc.queryForObject("SELECT count(*) FROM model_runtime." + table + " WHERE " + column + " = ?", Long.class, Long.parseLong(id));
    }

    private String login(boolean isPlatform, String tenant) {
        var body = new LinkedHashMap<String, Object>();
        body.put("loginName", isPlatform ? "platform-admin" : "demo-campus".equals(tenant) ? "tenant-admin-candidate" : "admin");
        body.put("password", "Demo@2026!");
        if (!isPlatform) body.put("tenantCode", tenant);
        var response = http.postForEntity(isPlatform ? "/api/v1/platform/auth/sessions" : "/api/v1/auth/sessions", body, JsonNode.class);
        // 只断言状态，不在失败输出中展开含会话令牌的响应。
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        var token = response.getBody().path("tokenValue").asString();
        assertFalse(token.isBlank());
        return token;
    }

    private JsonNode create(String token, Map<String, Object> body, String key) {
        var response = exchange(ROOT, token, body, key);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody() == null ? "无响应" : response.getBody().path("code").asString());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private JsonNode post(String path, String token, Map<String, ?> body, HttpStatus expected) {
        var response = exchange(path, token, body, null);
        assertEquals(expected, response.getStatusCode(), response.getBody() == null ? "无响应" :
                response.getBody().path("code").asString() + ":" + response.getBody().path("message").asString());
        return response.getBody();
    }

    private ResponseEntity<JsonNode> exchange(String path, String token, Object body, String key) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (key != null) headers.set("Idempotency-Key", key);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode get(String path, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var response = http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private boolean visible(String token, String id) {
        for (var item : get(CATALOG + "?pageNumber=1&pageSize=100", token).path("items"))
            if (id.equals(item.path("id").asString())) return true;
        return false;
    }

    private static SSLContext originalTls() {
        try { return SSLContext.getDefault(); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) System.clearProperty(key);
        else System.setProperty(key, previous);
    }

    /** 临时证书只信任本机合成厂商，保留主机名校验；用例结束后恢复信任并删除密钥库。 */
    private static HttpServer vendor() {
        Path directory = null;
        boolean started = false;
        try {
            directory = Files.createTempDirectory("wp0019-s2-test-tls-");
            var keyStorePath = directory.resolve("synthetic.p12");
            var password = "synthetic-test-only".toCharArray();
            var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "local-vendor", "-keyalg", "RSA", "-storetype", "PKCS12",
                    "-keystore", keyStorePath.toString(), "-storepass", new String(password),
                    "-dname", "CN=127.0.0.1", "-ext", "SAN=ip:127.0.0.1", "-validity", "1")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!command.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                command.destroyForcibly();
                throw new IllegalStateException("合成证书生成超时");
            }
            if (command.exitValue() != 0) throw new IllegalStateException("合成证书生成失败");
            var keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(keyStorePath)) { keys.load(input, password); }
            var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, password);
            var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(keys);
            var tls = SSLContext.getInstance("TLS");
            tls.init(managers.getKeyManagers(), trust.getTrustManagers(), null);
            var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(tls));
            server.setExecutor(VENDOR_THREADS);
            server.createContext("/v1/", PlatformSupplyWorkflowApiTest::reply);
            server.start();
            System.setProperty("javax.net.ssl.trustStore", keyStorePath.toString());
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
            System.setProperty("javax.net.ssl.trustStorePassword", new String(password));
            SSLContext.setDefault(tls);
            tlsDirectory = directory;
            started = true;
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException("无法启动隔离厂商传输", failure);
        } finally {
            if (!started && directory != null) {
                try {
                    Files.deleteIfExists(directory.resolve("synthetic.p12"));
                    Files.deleteIfExists(directory);
                } catch (IOException failure) { throw new IllegalStateException("合成证书缓存清理失败", failure); }
            }
        }
    }

    private static void reply(HttpExchange exchange) throws IOException {
        CALLS.incrementAndGet();
        var body = JSON.readTree(exchange.getRequestBody());
        Object response;
        switch (exchange.getRequestURI().getPath()) {
            case "/v1/chat/completions" -> {
                var input = body.path("messages").get(0).path("content").asString();
                var text = input.contains("UNKNOWN") ? "UNKNOWN" : input.contains("SAFE") ? "SAFE" : "OK";
                response = Map.of("id", "synthetic-chat", "object", "chat.completion", "created", 1,
                        "model", Type.CHAT.code(), "choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", text), "finish_reason", "stop")),
                        "usage", Map.of("prompt_tokens", 10, "completion_tokens", 1, "total_tokens", 11));
            }
            case "/v1/embeddings" -> response = Map.of("object", "list", "model", Type.EMBEDDING.code(),
                    "data", List.of(Map.of("object", "embedding", "index", 0,
                            "embedding", java.util.Collections.nCopies("dimension".equals(FAILURE.get()) ? 1023 : 1024, 1.0))),
                    "usage", Map.of("prompt_tokens", 5, "total_tokens", 5));
            case "/v1/rerank" -> {
                var documents = body.path("documents");
                var ranked = new ArrayList<Map<String, Object>>();
                for (var i = 0; i < documents.size(); i++)
                    if (documents.get(i).asString().equals("合成仓库周一维护。")) ranked.add(Map.of("index", i, "relevance_score", 1.5));
                for (var i = 0; i < documents.size(); i++)
                    if (!documents.get(i).asString().equals("合成仓库周一维护。")) ranked.add(Map.of("index", i, "relevance_score", 0.1));
                response = Map.of("id", "synthetic-rerank", "results", ranked, "meta", Map.of("tokens", Map.of("input_tokens", 12, "output_tokens", 0)));
            }
            default -> throw new IOException("隔离传输拒绝未登记协议");
        }
        var bytes = JSON.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private enum Type {
        CHAT, EMBEDDING, RERANK;
        long connection() { return 8101 + ordinal(); }
        String code() { return this == RERANK ? "BAAI/bge-reranker-v2-m3" : "synthetic-" + name().toLowerCase(java.util.Locale.ROOT); }
        URI endpoint() { return this == RERANK ? URI.create(ENDPOINT + "/rerank") : ENDPOINT; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalVendorOnly {
        /** 只对这个测试的随机环回端口放行；生产 SSRF 策略和全部真实协议适配器保持原样。 */
        @Bean
        OutboundEndpointPolicy outboundEndpointPolicy() {
            return uri -> {
                if (!ENDPOINT.equals(uri) && !Type.RERANK.endpoint().equals(uri))
                    throw new UnsafeOutboundEndpointException("测试只允许精确的本机合成端点");
            };
        }
    }
}
