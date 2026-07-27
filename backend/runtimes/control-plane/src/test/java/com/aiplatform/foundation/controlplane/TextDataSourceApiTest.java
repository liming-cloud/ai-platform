package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectMetadata;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.redis.testcontainers.RedisContainer;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("demo")
@Testcontainers
@Import(TextDataSourceApiTest.StorageConfiguration.class)
class TextDataSourceApiTest {

    private static final UUID STORE_TENANT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
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

    @Autowired
    private TestTextContentObjectStorage testStorage;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
    }

    @BeforeEach
    void clearKnowledgeData() {
        testStorage.clear();
        jdbc.update("DELETE FROM knowledge_data.outbox_event");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_request");
        jdbc.update("DELETE FROM knowledge_data.text_data_source_content");
        jdbc.update("DELETE FROM knowledge_data.text_data_source");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space_request");
        jdbc.update("DELETE FROM knowledge_data.knowledge_space");
    }

    @Test
    void 创建重放并列出当前租户知识空间的数据源() {
        var token = login("demo-store");
        var space = createSpace(token, "space-data-source-001", "门店商品知识");
        var headers = authorized(token);
        headers.add("Idempotency-Key", "source-http-0001");
        var request = new HttpEntity<>(
                Map.of(
                        "knowledgeSpaceId", space.id(),
                        "name", "门店商品文本"),
                headers);

        var created = http.postForEntity(
                url("/api/v1/data-sources"),
                request,
                SourceResponse.class);
        var replayed = http.postForEntity(
                url("/api/v1/data-sources"),
                request,
                SourceResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(HttpStatus.CREATED, replayed.getStatusCode());
        assertNotNull(created.getBody());
        assertNotNull(replayed.getBody());
        assertEquals(created.getBody().id(), replayed.getBody().id());
        assertEquals("DRAFT", created.getBody().status());
        assertEquals("true", replayed.getHeaders().getFirst("Idempotency-Replayed"));

        var listed = list(token, space.id());
        assertEquals(1, listed.items().size());
        assertEquals("门店商品文本", listed.items().getFirst().name());
    }

    @Test
    void 未登录和缺少写权限均不能创建数据源() {
        var token = login("demo-store");
        var space = createSpace(token, "space-data-source-002", "权限知识");

        var unauthenticated = http.getForEntity(
                url("/api/v1/data-sources?knowledgeSpaceId=" + space.id()),
                ApiError.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertNotNull(unauthenticated.getBody());
        assertEquals("AUTHENTICATION_REQUIRED", unauthenticated.getBody().code());

        var readOnly = sessions.issue(new TrustedRequestContext(
                new TenantId(STORE_TENANT),
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("knowledge:read")),
                "trace-data-source-read-only"));
        var headers = authorized(readOnly.tokenValue());
        headers.add("Idempotency-Key", "source-http-0002");
        var denied = http.postForEntity(
                url("/api/v1/data-sources"),
                new HttpEntity<>(
                        Map.of(
                                "knowledgeSpaceId", space.id(),
                                "name", "不得创建"),
                        headers),
                ApiError.class);

        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNotNull(denied.getBody());
        assertEquals("ACCESS_DENIED", denied.getBody().code());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_data.text_data_source",
                        Integer.class));
    }

    @Test
    void 缺少知识空间查询参数返回统一错误结构() {
        var token = login("demo-store");

        var response = http.exchange(
                url("/api/v1/data-sources"),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                ApiError.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_REQUEST", response.getBody().code());
        assertNotNull(response.getBody().traceId());
    }

    @Test
    void 两个租户的知识空间和数据源列表相互隔离() {
        var storeToken = login("demo-store");
        var campusToken = login("demo-campus");
        var storeSpace = createSpace(storeToken, "space-store-001", "门店知识");
        var campusSpace = createSpace(campusToken, "space-campus-001", "校园知识");
        createSource(storeToken, storeSpace.id(), "source-store-001", "门店文本");
        createSource(campusToken, campusSpace.id(), "source-campus-001", "校园文本");

        var store = list(storeToken, storeSpace.id());
        var campus = list(campusToken, campusSpace.id());
        var crossTenant = list(campusToken, storeSpace.id());

        assertEquals(List.of("门店文本"), store.items().stream().map(SourceResponse::name).toList());
        assertEquals(List.of("校园文本"), campus.items().stream().map(SourceResponse::name).toList());
        assertTrue(crossTenant.items().isEmpty());
    }

    @Test
    void 只有内容就绪且版本匹配的数据源才能发布并幂等重放() {
        var token = login("demo-store");
        var space = createSpace(token, "space-publish-001", "发布知识");
        var source = createSource(token, space.id(), "source-publish-001", "待发布文本");
        var uploadHeaders = authorized(token);
        uploadHeaders.add("Idempotency-Key", "upload-http-001");
        uploadHeaders.setContentType(new MediaType(
                "text",
                "plain",
                java.nio.charset.StandardCharsets.UTF_8));
        var uploadRequest = new HttpEntity<>("发布内容", uploadHeaders);
        var uploaded = http.postForEntity(
                url("/api/v1/data-sources/" + source.id()
                        + "/content?knowledgeSpaceId=" + space.id()
                        + "&expectedVersion=0"),
                uploadRequest,
                SourceResponse.class);
        var uploadReplay = http.postForEntity(
                url("/api/v1/data-sources/" + source.id()
                        + "/content?knowledgeSpaceId=" + space.id()
                        + "&expectedVersion=0"),
                uploadRequest,
                SourceResponse.class);
        assertEquals(HttpStatus.OK, uploaded.getStatusCode());
        assertEquals(HttpStatus.OK, uploadReplay.getStatusCode());
        assertNotNull(uploaded.getBody());
        assertEquals("CONTENT_READY", uploaded.getBody().status());
        assertEquals("true", uploadReplay.getHeaders().getFirst("Idempotency-Replayed"));

        var headers = authorized(token);
        headers.add("Idempotency-Key", "publish-http-001");
        var request = new HttpEntity<>(
                Map.of(
                        "knowledgeSpaceId", space.id(),
                        "expectedVersion", 1),
                headers);

        var published = http.postForEntity(
                url("/api/v1/data-sources/" + source.id() + "/publish"),
                request,
                SourceResponse.class);
        var replayed = http.postForEntity(
                url("/api/v1/data-sources/" + source.id() + "/publish"),
                request,
                SourceResponse.class);

        assertEquals(HttpStatus.OK, published.getStatusCode());
        assertEquals(HttpStatus.OK, replayed.getStatusCode());
        assertNotNull(published.getBody());
        assertEquals("PUBLISHED", published.getBody().status());
        assertEquals(1, published.getBody().publishedContentVersion());
        assertEquals("true", replayed.getHeaders().getFirst("Idempotency-Replayed"));
    }

    @Test
    void 对象元数据不一致时拒绝发布且不产生发布事实() {
        var token = login("demo-store");
        var space = createSpace(token, "space-integrity-001", "完整性知识");
        var source = createSource(token, space.id(), "source-integrity-001", "完整性文本");
        uploadContent(token, space.id(), source.id(), "upload-integrity-001", "完整性内容");
        testStorage.corruptAll();
        var headers = authorized(token);
        headers.add("Idempotency-Key", "publish-integrity-001");

        var response = http.postForEntity(
                url("/api/v1/data-sources/" + source.id() + "/publish"),
                new HttpEntity<>(
                        Map.of(
                                "knowledgeSpaceId", space.id(),
                                "expectedVersion", 1),
                        headers),
                ApiError.class);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TEXT_CONTENT_INTEGRITY_FAILURE", response.getBody().code());
        assertEquals(
                "CONTENT_READY",
                jdbc.queryForObject(
                        "SELECT status FROM knowledge_data.text_data_source "
                                + "WHERE tenant_id = ? AND id = ?",
                        String.class,
                        STORE_TENANT,
                        source.id()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_data.outbox_event "
                                + "WHERE event_type = 'TEXT_DATA_SOURCE_PUBLISHED'",
                        Integer.class));
    }

    private SpaceResponse createSpace(String token, String key, String name) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", key);
        var response = http.postForEntity(
                url("/api/v1/knowledge-spaces"),
                new HttpEntity<>(Map.of("name", name), headers),
                SpaceResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private SourceResponse createSource(
            String token,
            UUID knowledgeSpaceId,
            String key,
            String name) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", key);
        var response = http.postForEntity(
                url("/api/v1/data-sources"),
                new HttpEntity<>(
                        Map.of(
                                "knowledgeSpaceId", knowledgeSpaceId,
                                "name", name),
                        headers),
                SourceResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private SourceResponse uploadContent(
            String token,
            UUID knowledgeSpaceId,
            UUID dataSourceId,
            String key,
            String content) {
        var headers = authorized(token);
        headers.add("Idempotency-Key", key);
        headers.setContentType(new MediaType(
                "text",
                "plain",
                java.nio.charset.StandardCharsets.UTF_8));
        var response = http.postForEntity(
                url("/api/v1/data-sources/" + dataSourceId
                        + "/content?knowledgeSpaceId=" + knowledgeSpaceId
                        + "&expectedVersion=0"),
                new HttpEntity<>(content, headers),
                SourceResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private SourcePage list(String token, UUID knowledgeSpaceId) {
        var response = http.exchange(
                url("/api/v1/data-sources?knowledgeSpaceId=" + knowledgeSpaceId),
                HttpMethod.GET,
                new HttpEntity<>(authorized(token)),
                SourcePage.class);
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

    private record SpaceResponse(UUID id) {
    }

    private record SourceResponse(
            UUID id,
            UUID knowledgeSpaceId,
            String name,
            String status,
            long version,
            int contentVersion,
            int publishedContentVersion,
            Instant createdAt) {
    }

    private record SourcePage(List<SourceResponse> items, String nextCursor) {
    }

    private record ApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfiguration {

        @Bean
        @Primary
        TestTextContentObjectStorage textContentObjectStoragePort() {
            return new TestTextContentObjectStorage();
        }
    }

    private static final class TestTextContentObjectStorage
            implements TextContentObjectStoragePort {

        private final Map<String, TextContentObjectMetadata> objects =
                new ConcurrentHashMap<>();
        private final Map<String, byte[]> contents = new ConcurrentHashMap<>();

        @Override
        public TextContentReference put(TextContentStorageRequest request) {
            try (var input = request.content().openStream()) {
                var bytes = input.readAllBytes();
                if (bytes.length != request.content().byteSize()) {
                    throw new AssertionError("测试对象写入字节数不一致");
                }
                var objectKey = "foundation-v1/"
                        + request.tenantId().value() + "/"
                        + request.knowledgeSpaceId().value() + "/"
                        + request.dataSourceId().value() + "/"
                        + request.contentVersion() + "/"
                        + request.content().sha256() + ".txt";
                var reference = new TextContentReference(
                        request.contentVersion(),
                        objectKey,
                        request.content().sha256(),
                        request.content().byteSize(),
                        TextContentReference.UTF8_TEXT,
                        request.createdAt());
                objects.putIfAbsent(
                        objectKey,
                        new TextContentObjectMetadata(
                                objectKey,
                                reference.sha256(),
                                reference.byteSize(),
                                reference.mediaType()));
                contents.putIfAbsent(objectKey, bytes.clone());
                return reference;
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public TextContentObjectMetadata metadata(TextContentReference reference) {
            var metadata = objects.get(reference.objectKey());
            if (metadata == null) {
                throw new AssertionError("测试对象不存在");
            }
            return metadata;
        }

        @Override
        public InputStream read(TextContentReference reference) {
            var bytes = contents.get(reference.objectKey());
            if (bytes == null) {
                throw new AssertionError("测试对象不存在");
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void delete(TextContentReference reference) {
            objects.remove(reference.objectKey());
            contents.remove(reference.objectKey());
        }

        void clear() {
            objects.clear();
            contents.clear();
        }

        void corruptAll() {
            objects.replaceAll((key, value) -> new TextContentObjectMetadata(
                    key,
                    "f".repeat(64),
                    value.byteSize(),
                    value.mediaType()));
        }
    }
}
