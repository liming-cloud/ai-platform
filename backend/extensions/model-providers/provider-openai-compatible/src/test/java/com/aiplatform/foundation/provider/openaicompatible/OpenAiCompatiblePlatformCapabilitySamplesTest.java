package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilityCallException;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatiblePlatformCapabilitySamplesTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private final AtomicInteger calls = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();
    @BeforeEach void start() throws IOException {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor); server.start();
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }

    @Test void 方舟Chat使用SpringAI并显式关闭思考且返回实际用量() {
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            var body = JSON.readTree(exchange.getRequestBody());
            assertEquals(64, body.path("max_completion_tokens").intValue());
            assertEquals("disabled", body.path("thinking").path("type").textValue());
            assertEquals("只回复 OK。", body.path("messages").get(0).path("content").textValue());
            respond(exchange, 200, """
                    {"id":"fixture-chat","object":"chat.completion","created":1,"model":"fixture-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":10,"completion_tokens":1,"total_tokens":11}}
                    """);
        });
        var result = samples().execute(request(ModelType.CHAT, Duration.ofSeconds(2)));
        assertEquals("OK", result.text()); assertEquals(10L, result.inputTokens()); assertEquals(1L, result.outputTokens());
        assertFalse(result.unexpectedContent()); assertEquals(1, calls.get());
    }

    @Test void Embedding使用SpringAI单输入浮点协议且不向BGE发送dimensions() {
        server.createContext("/v1/embeddings", exchange -> {
            calls.incrementAndGet(); var body = JSON.readTree(exchange.getRequestBody());
            assertFalse(body.has("dimensions"));
            assertEquals(1, body.path("input").size());
            var vector = java.util.stream.IntStream.range(0, 1024).mapToObj(i -> "1.0").collect(java.util.stream.Collectors.joining(","));
            respond(exchange, 200, "{\"object\":\"list\",\"model\":\"fixture-model\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":["+vector+"]}],\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}}");
        });
        var result = samples().execute(request(ModelType.EMBEDDING, Duration.ofSeconds(2)));
        assertEquals(1, result.embeddings().size()); assertEquals(1024, result.embeddings().getFirst().values().size());
        assertEquals(0, result.embeddings().getFirst().index()); assertEquals(5L, result.inputTokens()); assertEquals(0L, result.outputTokens());
        assertEquals(1, calls.get());
    }

    @Test void 独立RERANK仅发送固定文本重排字段且不把得分强制归一化() {
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet(); var body = JSON.readTree(exchange.getRequestBody());
            assertEquals("仓库维护日", body.path("query").textValue()); assertEquals(3, body.path("documents").size());
            assertEquals(3, body.path("top_n").intValue()); assertFalse(body.path("return_documents").booleanValue());
            assertFalse(body.has("messages")); assertFalse(body.has("input"));
            respond(exchange, 200, """
                    {"id":"fixture-rerank","results":[{"index":0,"relevance_score":1.5},{"index":2,"relevance_score":0.4},{"index":1,"relevance_score":-0.2}],
                     "meta":{"tokens":{"input_tokens":12,"output_tokens":0}}}
                    """);
        });
        var result = samples().rerank(request(ModelType.RERANK, Duration.ofSeconds(2)));
        assertEquals(List.of(0, 2, 1), result.ranks().stream().map(r -> r.index()).toList());
        assertEquals(1.5, result.ranks().getFirst().score()); assertEquals(12L, result.inputTokens()); assertEquals(1, calls.get());
    }

    @Test void 私网或非对应能力在平台凭据解析之前拒绝() {
        var resolved = new AtomicInteger();
        var sample = new OpenAiCompatiblePlatformCapabilitySamples((ref, revision) -> {
            resolved.incrementAndGet(); return SecretValue.copyOf("synthetic-key".toCharArray());
        }, uri -> { throw new UnsafeOutboundEndpointException("synthetic"); });
        var error = assertThrows(PlatformCapabilityCallException.class, () -> sample.execute(request(ModelType.CHAT, Duration.ofSeconds(2))));
        assertEquals(PlatformCapabilityCallException.Reason.ENDPOINT_REJECTED, error.reason()); assertEquals(0, resolved.get());
        assertThrows(PlatformCapabilityCallException.class, () -> sample.execute(request(ModelType.RERANK, Duration.ofSeconds(2))));
        assertEquals(0, resolved.get());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 403, 429, 302, 503})
    void 认证错误重定向和503均不读取原文或自动重发(int status) {
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet(); exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.getResponseHeaders().set("Location", "/stolen"); respond(exchange, status, "synthetic-key");
        });
        var error = assertThrows(PlatformCapabilityCallException.class, () -> samples().execute(request(ModelType.CHAT, Duration.ofSeconds(2))));
        assertFalse(error.toString().contains("synthetic-key")); assertNull(error.getCause()); assertEquals(1, calls.get());
    }

    @Test void 收到响应头后停滞仍受整次网络期限约束() {
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet(); exchange.sendResponseHeaders(200, 100); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } exchange.close();
        });
        var error = assertThrows(PlatformCapabilityCallException.class, () -> samples().rerank(request(ModelType.RERANK, Duration.ofMillis(150))));
        assertEquals(PlatformCapabilityCallException.Reason.TIMEOUT, error.reason()); assertEquals(1, calls.get());
    }

    @Test void 非向量能力不能携带负数向量维度() {
        var valid = request(ModelType.CHAT, Duration.ofSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> new PlatformCapabilitySampleRequest(valid.binding(), valid.type(), valid.providerPresetId(),
                valid.endpoint(), valid.credentialReference(), valid.connectionRevision(), valid.modelCode(), valid.input(), valid.documents(),
                valid.maxOutputTokens(), -1, 0, valid.timeout()));
    }

    @Test void 正文正确但混入工具调用仍必须标记为非预期内容() {
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, """
                    {"id":"fixture-tool","object":"chat.completion","created":1,"model":"fixture-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"OK", "tool_calls":[{"id":"fixture-call","type":"function","function":{"name":"fixture-tool","arguments":"{}"}}]},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":10,"completion_tokens":1,"total_tokens":11}}
                    """);
        });
        assertTrue(samples().execute(request(ModelType.CHAT, Duration.ofSeconds(2))).unexpectedContent(), "不能只检查正文和 finish_reason");
        assertEquals(1, calls.get());
    }

    @Test void 解析凭据消耗全部期限后不再发起网络请求() {
        var adapter = new OpenAiCompatiblePlatformCapabilitySamples((ref, revision) -> {
            try { Thread.sleep(180); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return SecretValue.copyOf("synthetic-key".toCharArray());
        }, uri -> {});
        var error = assertThrows(PlatformCapabilityCallException.class,
                () -> adapter.execute(request(ModelType.CHAT, Duration.ofMillis(50))));
        assertEquals(PlatformCapabilityCallException.Reason.TIMEOUT, error.reason()); assertEquals(0, calls.get());
    }

    @Test void 重排输出用量超出Long不能截断为零() {
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, """
                    {"results":[{"index":0,"relevance_score":0.9},{"index":1,"relevance_score":0.6},{"index":2,"relevance_score":0.2}],
                     "meta":{"tokens":{"input_tokens":12,"output_tokens":18446744073709551616}}}
                    """);
        });
        assertThrows(PlatformCapabilityCallException.class, () -> samples().rerank(request(ModelType.RERANK, Duration.ofSeconds(2))));
        assertEquals(1, calls.get());
    }

    @Test void 取消已收到响应头的请求会终止正文读取且不重试() throws Exception {
        var received = new java.util.concurrent.CountDownLatch(1);
        var failure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        server.createContext("/v1/rerank", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 100); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush(); received.countDown();
            try { Thread.sleep(3000); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } exchange.close();
        });
        var worker = Thread.ofVirtual().start(() -> {
            try { samples().rerank(request(ModelType.RERANK, Duration.ofSeconds(10))); }
            catch (RuntimeException exception) { failure.set(exception); }
        });
        try {
            assertTrue(received.await(2, java.util.concurrent.TimeUnit.SECONDS)); worker.interrupt();
            assertTrue(worker.join(Duration.ofSeconds(2)), "取消不能等到整轮网络超时");
            var error = assertInstanceOf(PlatformCapabilityCallException.class, failure.get());
            assertEquals(PlatformCapabilityCallException.Reason.CANCELLED, error.reason()); assertEquals(1, calls.get());
        } finally { worker.interrupt(); }
    }

    @Test void 缺少原生用量不能用SDK默认值冒充验证材料() {
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, """
                    {"id":"fixture-no-usage","object":"chat.completion","created":1,"model":"fixture-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}
                    """);
        });
        assertThrows(PlatformCapabilityCallException.class, () -> samples().execute(request(ModelType.CHAT, Duration.ofSeconds(2))));
        assertEquals(1, calls.get());
    }

    private OpenAiCompatiblePlatformCapabilitySamples samples() {
        return new OpenAiCompatiblePlatformCapabilitySamples((ref, revision) -> SecretValue.copyOf("synthetic-key".toCharArray()), uri -> {});
    }
    private PlatformCapabilitySampleRequest request(ModelType type, Duration timeout) {
        return new PlatformCapabilitySampleRequest("a".repeat(64), type, type == ModelType.CHAT ? "VOLCENGINE" : "SILICONFLOW",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + (type == ModelType.RERANK ? "/v1/rerank" : "/v1")),
                "secret://platform-provider-connection/101", 2, "fixture-model", type == ModelType.RERANK ? "仓库维护日" : "只回复 OK。",
                type == ModelType.RERANK ? List.of("合成仓库周一维护。", "合成餐厅周二营业。", "合成车库周三开放。") : List.of(),
                type == ModelType.CHAT ? 64 : 0, type == ModelType.EMBEDDING ? 1024 : 0, type == ModelType.RERANK ? 3 : 0, timeout);
    }
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException {
        var bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(code,bytes.length);
        try (var stream=exchange.getResponseBody()) { stream.write(bytes); }
    }
}
