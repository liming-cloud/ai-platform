package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatiblePlatformModelDeploymentProbeTest {
    private HttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void 平台最小调用不构造租户且秘密在完成后关闭() {
        var reference = new AtomicReference<String>();
        var secret = SecretValue.copyOf("synthetic-platform-key".toCharArray());
        server.createContext("/v1/chat/completions", exchange -> {
            assertEquals("Bearer synthetic-platform-key", exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"id":"synthetic","object":"chat.completion","created":1,"model":"synthetic-chat",
                    "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                    "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """);
        });
        var probe = new OpenAiCompatiblePlatformModelDeploymentProbe(value -> {
            reference.set(value); return secret;
        }, endpoint -> {});
        var result = probe.probe(request(Set.of(ModelCapability.CHAT)));
        assertTrue(result.reachable()); assertEquals(Set.of(ModelCapability.CHAT), result.capabilities());
        assertEquals("secret://platform-provider/test", reference.get());
        assertThrows(IllegalStateException.class, () -> secret.use(chars -> null));
    }

    @Test
    void 受限平台适配器不能把重排当聊天() {
        var resolved = new AtomicBoolean();
        var probe = new OpenAiCompatiblePlatformModelDeploymentProbe(value -> {
            resolved.set(true); throw new AssertionError("不应解析秘密");
        }, endpoint -> { throw new AssertionError("不应检查不存在的调用"); });
        var result = probe.probe(request(Set.of(ModelCapability.RERANK)));
        assertFalse(result.reachable()); assertTrue(result.capabilities().isEmpty());
        assertEquals("OPENAI_COMPATIBLE_CAPABILITY_UNSUPPORTED", result.detailCode());
        assertFalse(resolved.get());
    }

    @Test
    void 出站拒绝必须先于平台凭据解析() {
        var resolved = new AtomicBoolean();
        var probe = new OpenAiCompatiblePlatformModelDeploymentProbe(value -> {
            resolved.set(true); throw new AssertionError("不应解析秘密");
        }, endpoint -> { throw new UnsafeOutboundEndpointException("synthetic-denial"); });
        var result = probe.probe(request(Set.of(ModelCapability.CHAT)));
        assertEquals("OPENAI_COMPATIBLE_ENDPOINT_REJECTED", result.detailCode()); assertFalse(resolved.get());
    }

    @Test
    void 平台限流不得重试且失败秘密关闭不泄漏原响应() {
        var calls = new AtomicInteger();
        var secret = SecretValue.copyOf("synthetic-platform-key".toCharArray());
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet(); respond(exchange, 429, "{\"error\":{\"message\":\"synthetic-platform-key\"}}");
        });
        var result = new OpenAiCompatiblePlatformModelDeploymentProbe(value -> secret, endpoint -> {})
                .probe(request(Set.of(ModelCapability.CHAT)));
        assertFalse(result.reachable()); assertEquals("OPENAI_COMPATIBLE_RATE_LIMITED", result.detailCode());
        assertEquals(1, calls.get()); assertFalse(result.toString().contains("synthetic-platform-key"));
        assertThrows(IllegalStateException.class, () -> secret.use(chars -> null));
    }

    private PlatformModelDeploymentProbeRequest request(Set<ModelCapability> capabilities) {
        return new PlatformModelDeploymentProbeRequest(ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                "secret://platform-provider/test", "synthetic-chat", capabilities, Duration.ofSeconds(2));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
