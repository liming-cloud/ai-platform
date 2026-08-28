package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatiblePlatformProviderModelDiscovererTest {

    private static final String SECRET = "discovery-only-secret";
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
    void 应从权威Models接口发现未知候选且不按名称猜测能力() {
        server.createContext("/v1/models", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("Bearer " + SECRET, exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "object": "list",
                      "data": [
                        {"id": "definitely-embedding-looking-name", "object": "model", "owned_by": "vendor"},
                        {"id": "chat-looking-name", "object": "model", "owned_by": "vendor"}
                      ]
                    }
                    """);
        });
        var discoverer = new OpenAiCompatiblePlatformProviderModelDiscoverer(
                reference -> SecretValue.copyOf(SECRET.toCharArray()),
                endpoint -> {
                    // 仅测试进程显式允许回环地址，生产策略不会放行。
                });

        var observation = discoverer.discover(request());

        assertTrue(observation.succeeded());
        assertEquals("OPENAI_COMPATIBLE_DISCOVERY_OK", observation.resultCode());
        assertEquals(2, observation.candidates().size());
        assertTrue(observation.candidates().stream().allMatch(candidate ->
                candidate.classification() == PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION
                        && candidate.modelType() == null
                        && candidate.capabilities().isEmpty()));
    }

    @Test
    void 应在出站策略拒绝时停止秘密解析和网络调用() {
        var resolved = new AtomicBoolean();
        var discoverer = new OpenAiCompatiblePlatformProviderModelDiscoverer(
                reference -> {
                    resolved.set(true);
                    return SecretValue.copyOf(SECRET.toCharArray());
                },
                endpoint -> {
                    throw new UnsafeOutboundEndpointException("拒绝测试地址");
                });

        var observation = discoverer.discover(request());

        assertFalse(observation.succeeded());
        assertEquals("OPENAI_COMPATIBLE_DISCOVERY_ENDPOINT_REJECTED", observation.resultCode());
        assertFalse(resolved.get());
    }

    @Test
    void 应将供应商原始认证响应收敛为稳定失败分类() {
        server.createContext("/v1/models", exchange ->
                respond(exchange, 401, "{\"error\":{\"message\":\"" + SECRET + "\"}}"));
        var discoverer = new OpenAiCompatiblePlatformProviderModelDiscoverer(
                reference -> SecretValue.copyOf(SECRET.toCharArray()),
                endpoint -> {
                });

        var observation = discoverer.discover(request());

        assertFalse(observation.succeeded());
        assertEquals("OPENAI_COMPATIBLE_DISCOVERY_AUTHENTICATION_FAILED", observation.resultCode());
        assertFalse(observation.resultCode().contains(SECRET));
    }

    private PlatformProviderModelDiscoveryRequest request() {
        return new PlatformProviderModelDiscoveryRequest(
                ProviderKind.OPENAI_COMPATIBLE,
                PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                new CredentialReference("secret://test/openai"),
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
