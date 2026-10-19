package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderDiscoveryCredentialReferences;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VolcengineArkPlatformProviderModelDiscovererTest {

    private static final Instant NOW = Instant.parse("2026-08-24T09:00:00Z");
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 应使用官方ListEndpoints签名分页获取候选且不按模型名称猜测能力() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            var page = calls.incrementAndGet();
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("Action=ListEndpoints&Version=2024-01-01", exchange.getRequestURI().getRawQuery());
            assertEquals("20260824T090000Z", exchange.getRequestHeaders().getFirst("X-Date"));
            assertTrue(exchange.getRequestHeaders().getFirst("Authorization")
                    .startsWith("HMAC-SHA256 Credential=AK_TEST/20260824/cn-beijing/ark/request,"));
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("{\"PageNumber\":" + page + ",\"PageSize\":100}", body);
            respond(exchange, 200, page == 1 ? """
                    {"ResponseMetadata":{"RequestId":"vendor-internal"},"Result":{
                      "Items":[{"Id":"ep-202608240001","Name":"embedding-looking-name", "EndpointModelType":"Foundation"}],
                      "PageNumber":1,"PageSize":1,"TotalCount":2}}
                    """ : """
                    {"ResponseMetadata":{"RequestId":"vendor-internal"},"Result":{
                      "Items":[{"Id":"ep-202608240002","Name":"chat-looking-name", "EndpointModelType":"Custom"}],
                      "PageNumber":2,"PageSize":1,"TotalCount":2}}
                    """);
        });
        var discoverer = new VolcengineArkPlatformProviderModelDiscoverer(
                reference -> reference.contains("access-key/")
                        ? SecretValue.copyOf("AK_TEST".toCharArray())
                        : SecretValue.copyOf("SK_TEST".toCharArray()),
                endpoint -> {
                    // 仅测试进程显式允许回环地址，生产策略不会放行。
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                region -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

        var observation = discoverer.discover(request());

        assertTrue(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_OK", observation.resultCode());
        assertEquals(2, calls.get());
        assertEquals(2, observation.candidates().size());
        assertTrue(observation.candidates().stream().allMatch(candidate ->
                candidate.modelCode().startsWith("ep-")
                        && candidate.classification()
                                == PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION
                        && candidate.modelType() == null
                        && candidate.capabilities().isEmpty()));
    }

    @Test
    void 空接入点列表不能形成成功验证且只请求一次() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, """
                    {"ResponseMetadata":{},"Result":{
                      "Items":[],"PageNumber":1,"PageSize":100,"TotalCount":0}}
                    """);
        });
        var discoverer = new VolcengineArkPlatformProviderModelDiscoverer(
                reference -> SecretValue.copyOf("TEST_DISCOVERY_CREDENTIAL".toCharArray()),
                endpoint -> {},
                Clock.fixed(NOW, ZoneOffset.UTC),
                region -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

        var observation = discoverer.discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_NO_MODELS", observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void 应隐藏方舟原始错误和内部标识() {
        server.createContext("/", exchange -> respond(exchange, 200, """
                {"ResponseMetadata":{"RequestId":"vendor-request-secret","Error":{
                  "Code":"InternalVendorCode","Message":"SK_TEST"}}}
                """));
        var discoverer = new VolcengineArkPlatformProviderModelDiscoverer(
                reference -> reference.contains("access-key/")
                        ? SecretValue.copyOf("AK_TEST".toCharArray())
                        : SecretValue.copyOf("SK_TEST".toCharArray()),
                endpoint -> {
                },
                Clock.fixed(NOW, ZoneOffset.UTC),
                region -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

        var observation = discoverer.discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_CALL_FAILED", observation.resultCode());
        assertFalse(observation.resultCode().contains("InternalVendorCode"));
        assertFalse(observation.resultCode().contains("vendor-request-secret"));
        assertFalse(observation.resultCode().contains("SK_TEST"));
    }

    @Test
    void 签名必须与官方HMACSHA256规范固定向量一致() {
        var body = "{\"PageNumber\":1,\"PageSize\":100}".getBytes(StandardCharsets.UTF_8);

        var signed = VolcengineSignerV4.sign(
                URI.create("https://ark.cn-beijing.volcengineapi.com/"
                        + "?Action=ListEndpoints&Version=2024-01-01"),
                "POST",
                body,
                "AK_TEST".toCharArray(),
                "SK_TEST".toCharArray(),
                "cn-beijing",
                "ark",
                NOW);

        assertEquals("058d32c09f9efb08808431ec9c1f0868f6306c9d7b8d68e88a18583010150af3",
                signed.contentSha256());
        assertEquals("HMAC-SHA256 Credential=AK_TEST/20260824/cn-beijing/ark/request, "
                        + "SignedHeaders=content-type;host;x-content-sha256;x-date, "
                        + "Signature=d1d9f633b8a9d5dd732c573cdea1ae1e74497aaa7ac81f03e73acc2d5999b46d",
                signed.authorization());
    }

    private PlatformProviderModelDiscoveryRequest request() {
        return new PlatformProviderModelDiscoveryRequest(
                ProviderKind.OPENAI_COMPATIBLE,
                PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE,
                URI.create("https://ark.cn-beijing.volces.com/api/v3"),
                "cn-beijing",
                "secret://platform-provider-connection/320000000000000001",
                Optional.of(new PlatformProviderDiscoveryCredentialReferences(
                        "secret://platform-provider-discovery-access-key/320000000000000001",
                        "secret://platform-provider-discovery-secret-key/320000000000000001")),
                Duration.ofSeconds(2));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
