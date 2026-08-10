package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleConnectionProbeTest {

    private static final String SECRET = "probe-only-secret";
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
    void 应通过SpringAi执行最小聊天调用并识别能力() {
        server.createContext("/v1/chat/completions", exchange -> {
            assertEquals("Bearer " + SECRET, exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, successResponse());
        });
        var probe = new OpenAiCompatibleConnectionProbe(
                reference -> SecretValue.copyOf(SECRET.toCharArray()),
                endpoint -> {
                    // 仅测试进程显式允许回环地址，生产策略不会放行。
                });

        var observation = probe.probe(request());

        assertTrue(observation.reachable());
        assertEquals("OPENAI_COMPATIBLE_OK", observation.detailCode());
        assertEquals(
                java.util.Set.of(ModelCapability.CHAT),
                observation.capabilities());
    }

    @Test
    void 应按待验证能力执行最小嵌入调用() {
        server.createContext("/v1/embeddings", exchange ->
                respond(exchange, 200, """
                        {
                          "object": "list",
                          "model": "demo-embedding",
                          "data": [
                            {"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}
                          ],
                          "usage": {"prompt_tokens": 1, "total_tokens": 1}
                        }
                        """));
        var probe = new OpenAiCompatibleConnectionProbe(
                reference -> SecretValue.copyOf(SECRET.toCharArray()),
                endpoint -> {
                });

        var observation = probe.probe(request(
                "demo-embedding",
                Set.of(ModelCapability.EMBEDDING)));

        assertTrue(observation.reachable());
        assertEquals(Set.of(ModelCapability.EMBEDDING), observation.capabilities());
    }

    @Test
    void 应在出站策略拒绝时停止密钥解析和网络调用() {
        var resolved = new AtomicBoolean();
        var probe = new OpenAiCompatibleConnectionProbe(
                reference -> {
                    resolved.set(true);
                    return SecretValue.copyOf(SECRET.toCharArray());
                },
                endpoint -> {
                    throw new UnsafeOutboundEndpointException("拒绝测试地址");
                });

        var observation = probe.probe(request());

        assertFalse(observation.reachable());
        assertEquals("OPENAI_COMPATIBLE_ENDPOINT_REJECTED", observation.detailCode());
        assertFalse(resolved.get());
    }

    @Test
    void 应将认证失败归类为稳定错误码且不泄漏响应内容() {
        server.createContext("/v1/chat/completions", exchange ->
                respond(exchange, 401, "{\"error\":{\"message\":\"" + SECRET + "\"}}"));
        var probe = new OpenAiCompatibleConnectionProbe(
                reference -> SecretValue.copyOf(SECRET.toCharArray()),
                endpoint -> {
                    // 仅测试进程显式允许回环地址，生产策略不会放行。
                });

        var observation = probe.probe(request());

        assertFalse(observation.reachable());
        assertEquals("OPENAI_COMPATIBLE_AUTHENTICATION_FAILED", observation.detailCode());
        assertFalse(observation.detailCode().contains(SECRET));
    }

    private ProviderProbeRequest request() {
        return request("demo-chat", Set.of(ModelCapability.CHAT));
    }

    private ProviderProbeRequest request(
            String modelCode,
            Set<ModelCapability> capabilities) {
        return new ProviderProbeRequest(
                new TenantId(100000000000000001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                new CredentialReference("secret://test/openai"),
                modelCode,
                capabilities,
                Duration.ofSeconds(2));
    }

    private static String successResponse() {
        return """
                {
                  "id": "chatcmpl-probe",
                  "object": "chat.completion",
                  "created": 1785080000,
                  "model": "demo-chat",
                  "choices": [
                    {
                      "index": 0,
                      "message": {"role": "assistant", "content": "ok"},
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 1,
                    "completion_tokens": 1,
                    "total_tokens": 2
                  }
                }
                """;
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
