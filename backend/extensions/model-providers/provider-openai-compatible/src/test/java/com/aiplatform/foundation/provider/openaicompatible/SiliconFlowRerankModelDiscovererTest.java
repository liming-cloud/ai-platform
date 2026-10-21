package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SiliconFlowRerankModelDiscovererTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger resolved = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); executor.shutdownNow(); }

    @Test
    void 仅账户真实列表里的精确映射模型获得RERANK分类且不编造名称() {
        server.createContext("/v1/models", exchange -> {
            requests.incrementAndGet();
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("sub_type=reranker", exchange.getRequestURI().getRawQuery());
            assertEquals("Bearer synthetic-only-key", exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"data\":[{\"id\":\"BAAI/bge-reranker-v2-m3\"},{\"id\":\"some-rerank-looking-id\"}]}");
        });
        var result = discoverer().discover(request(Duration.ofSeconds(2)));
        assertTrue(result.succeeded());
        assertEquals(2, result.candidates().size());
        var mapped = result.candidates().stream().filter(c -> c.modelCode().equals("BAAI/bge-reranker-v2-m3")).findFirst().orElseThrow();
        assertEquals(PlatformModelCandidateClassification.CONFIRMED_BY_CONTROLLED_MAPPING, mapped.classification());
        assertEquals("RERANK", mapped.modelType().name());
        assertNull(mapped.displayName());
        assertEquals(SiliconFlowRerankModelDiscoverer.MAPPING_DIGEST, mapped.source().mappingDigest());
        assertEquals(SiliconFlowRerankModelDiscoverer.MAPPING_DIGEST, java.util.HexFormat.of().formatHex(
                sha256((SiliconFlowRerankModelDiscoverer.MAPPING_VERSION + "\nBAAI/bge-reranker-v2-m3|RERANK|RERANK\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertEquals(SiliconFlowRerankModelDiscoverer.LIST_SOURCE.toString(), mapped.source().listSource());
        var other = result.candidates().stream().filter(c -> c.modelCode().equals("some-rerank-looking-id")).findFirst().orElseThrow();
        assertEquals(PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION, other.classification());
        assertNull(other.modelType());
        assertTrue(other.capabilities().isEmpty());
        assertEquals(mapped.source(), other.source());
        assertEquals(1, requests.get());
    }

    @Test
    void 空列表成功且不会凭受控映射补齐厂商不存在的模型() {
        server.createContext("/v1/models", exchange -> respond(exchange, 200, "{\"data\":[]}"));
        var result = discoverer().discover(request(Duration.ofSeconds(2)));
        assertTrue(result.succeeded()); assertTrue(result.candidates().isEmpty());
    }

    @Test
    void 出站策略校验固定官方地址并在拒绝时不解析秘密() {
        var result = new SiliconFlowRerankModelDiscoverer(ref -> {
            resolved.incrementAndGet(); return SecretValue.copyOf("synthetic-only-key".toCharArray());
        }, endpoint -> {
            assertEquals(SiliconFlowRerankModelDiscoverer.LIST_SOURCE, endpoint);
            throw new UnsafeOutboundEndpointException("synthetic");
        }).discover(request(Duration.ofSeconds(2)));
        assertEquals("SILICONFLOW_RERANK_DISCOVERY_ENDPOINT_REJECTED", result.resultCode());
        assertEquals(0, resolved.get());
    }

    @Test
    void 认证限流与重定向只调用一次且不回显原始正文() {
        for (int code : new int[]{401, 403, 429, 302, 500, 503}) {
            server.createContext("/v1/models", exchange -> {
                requests.incrementAndGet();
                exchange.getResponseHeaders().set("Location", "/redirected");
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, code, "synthetic-only-key");
            });
            var result = discoverer().discover(request(Duration.ofSeconds(2)));
            assertFalse(result.succeeded()); assertTrue(result.candidates().isEmpty());
            assertFalse(result.toString().contains("synthetic-only-key"));
            server.removeContext("/v1/models");
        }
        assertEquals(6, requests.get());
    }

    @Test
    void 畸形重复尾随JSON与超大正文失败且不留下部分候选() {
        for (String body : new String[]{"{\"data\":[null]}", "{\"data\":[{\"id\":\"x\"},{\"id\":\"x\"}]}",
                "{\"data\":[{\"id\":\"BAAI/bge-reranker-v2-m3\"}]}{}", "{\"data\":[{\"id\":\"\"}]}",
                " ".repeat(1_048_577)}) {
            server.createContext("/v1/models", exchange -> respond(exchange, 200, body));
            var result = discoverer().discover(request(Duration.ofSeconds(2)));
            assertFalse(result.succeeded(), "畸形响应不能成为成功发现");
            assertTrue(result.candidates().isEmpty());
            server.removeContext("/v1/models");
        }
    }

    @Test
    void 厂商把认证材料编码放入模型编码时不得返回或持久化候选() {
        var key = "synthetic-only-key";
        var encoded = java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
        for (var value : java.util.List.of(key, "model-" + key,
                java.util.Base64.getEncoder().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8)))) {
            server.createContext("/v1/models", exchange -> respond(exchange, 200, "{\"data\":[{\"id\":\"" + value + "\"}]}"));
            var result = discoverer().discover(request(Duration.ofSeconds(2)));
            assertFalse(result.succeeded());
            assertTrue(result.candidates().isEmpty());
            assertFalse(result.toString().contains(value));
            server.removeContext("/v1/models");
        }
    }

    @Test
    void 总期限覆盖收到响应头后停滞的正文且不自动重试() {
        server.createContext("/v1/models", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        var result = discoverer().discover(request(Duration.ofMillis(150)));
        assertEquals("SILICONFLOW_RERANK_DISCOVERY_TIMEOUT", result.resultCode());
        assertEquals(1, requests.get());
    }

    private SiliconFlowRerankModelDiscoverer discoverer() {
        return new SiliconFlowRerankModelDiscoverer(ref -> SecretValue.copyOf("synthetic-only-key".toCharArray()),
                endpoint -> assertEquals(SiliconFlowRerankModelDiscoverer.LIST_SOURCE, endpoint),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/models?sub_type=reranker"));
    }

    private static PlatformProviderModelDiscoveryRequest request(Duration timeout) {
        return new PlatformProviderModelDiscoveryRequest(ProviderKind.OPENAI_COMPATIBLE,
                PlatformModelDiscoveryProfile.SILICONFLOW_RERANK_MODELS_API, URI.create("https://api.siliconflow.cn/v1/rerank"),
                "cn", "secret://synthetic", Optional.empty(), timeout);
    }

    private static byte[] sha256(byte[] value) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
