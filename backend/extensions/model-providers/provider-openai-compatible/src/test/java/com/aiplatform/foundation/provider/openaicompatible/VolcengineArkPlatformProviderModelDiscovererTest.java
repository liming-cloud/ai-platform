package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void 应签名分页读取两个default来源且不按模型名称猜测能力() {
        var calls = new AtomicInteger();
        var customPages = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            var preset = isPreset(exchange);
            var page = preset ? 1 : customPages.incrementAndGet();
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("Action=" + (preset ? "InnerDescribeModelEndpoints" : "ListEndpoints")
                    + "&Version=2024-01-01", exchange.getRequestURI().getRawQuery());
            assertEquals("20260824T090000Z", exchange.getRequestHeaders().getFirst("X-Date"));
            assertTrue(exchange.getRequestHeaders().getFirst("Authorization")
                    .startsWith("HMAC-SHA256 Credential=AK_TEST/20260824/cn-beijing/ark/request,"));
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("{\"PageNumber\":" + page + ",\"PageSize\":100,\"ProjectName\":\"default\"}", body);
            respond(exchange, 200, preset ? page("[]", 1, 100, 0) : page == 1 ? """
                    {"ResponseMetadata":{"RequestId":"vendor-internal"},"Result":{
                      "Items":[{"Id":"ep-202608240001","ProjectName":"default","Name":"embedding-looking-name", "EndpointModelType":"Foundation"}],
                      "PageNumber":1,"PageSize":1,"TotalCount":2}}
                    """ : """
                    {"ResponseMetadata":{"RequestId":"vendor-internal"},"Result":{
                      "Items":[{"Id":"ep-202608240002","ProjectName":"default","Name":"chat-looking-name", "EndpointModelType":"Custom"}],
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
        assertEquals(3, calls.get());
        assertEquals(2, observation.candidates().size());
        assertTrue(observation.candidates().stream().allMatch(candidate ->
                candidate.modelCode().startsWith("ep-")
                        && candidate.classification()
                                == PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION
                        && candidate.modelType() == null
                        && candidate.capabilities().isEmpty()));
    }

    @Test
    void 自定义为空时应发现default预置模型并使用ModelId且保持待确认() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            boolean preset = exchange.getRequestURI().getRawQuery()
                    .equals("Action=InnerDescribeModelEndpoints&Version=2024-01-01");
            respond(exchange, 200, preset ? """
                    {"ResponseMetadata":{},"Result":{
                      "Items":[{"Id":"ep-m-default:official-model-id","ModelId":"official-model-id",
                        "ProjectName":"default","Status":"Running","Name":"embedding-looking-name"}],
                      "PageNumber":1,"PageSize":100,"TotalCount":1}}
                    """ : """
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

        assertTrue(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_OK", observation.resultCode());
        assertEquals(2, calls.get());
        assertEquals(1, observation.candidates().size());
        var candidate = observation.candidates().iterator().next();
        assertEquals("official-model-id", candidate.modelCode());
        assertEquals("embedding-looking-name", candidate.displayName());
        assertEquals(PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION,
                candidate.classification());
        assertNull(candidate.modelType());
        assertTrue(candidate.capabilities().isEmpty());
    }

    @Test
    void 两来源均为空时不能形成成功验证且不重试() {
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
        assertEquals(2, calls.get());
    }

    @Test
    void 两来源非空时应合并并按模型编码去重() {
        server.createContext("/", exchange -> respond(exchange, 200, isPreset(exchange)
                ? page("""
                        [{"ModelId":"official-model-id","Name":"预置来源名称","ProjectName":"default","Status":"Running"},
                         {"ModelId":"official-model-id","Name":"同码其他名称","ProjectName":"default","Status":"Running"}]
                        """, 1, 100, 2)
                : page("""
                        [{"Id":"ep-custom","ProjectName":"default"},
                         {"Id":"official-model-id","Name":"自定义来源名称","ProjectName":"default"}]
                        """, 1, 100, 2)));

        var observation = discoverer().discover(request());

        assertTrue(observation.succeeded());
        assertEquals(2, observation.candidates().size());
        assertEquals(Set.of("ep-custom", "official-model-id"), observation.candidates().stream()
                .map(candidate -> candidate.modelCode()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("自定义来源名称", observation.candidates().stream()
                .filter(candidate -> candidate.modelCode().equals("official-model-id"))
                .findFirst().orElseThrow().displayName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Stopped", "Scheduling", "Abnormal", "UnknownStatus"})
    void 非运行预置不能成为候选也不能遮蔽运行中的记录(String status) {
        server.createContext("/", exchange -> respond(exchange, 200, isPreset(exchange)
                ? page("[{\"ModelId\":\"not-ready\",\"ProjectName\":\"default\",\"Status\":\"" + status + "\"},"
                        + "{\"ModelId\":\"ready\",\"ProjectName\":\"default\",\"Status\":\"Running\"}]", 1, 100, 2)
                : page("[]", 1, 100, 0)));

        var observation = discoverer().discover(request());

        assertTrue(observation.succeeded());
        assertEquals(1, observation.candidates().size());
        assertEquals("ready", observation.candidates().iterator().next().modelCode());
    }

    @Test
    void 只有非运行预置时仍应记录零候选失败() {
        server.createContext("/", exchange -> respond(exchange, 200, isPreset(exchange)
                ? page("[{\"ModelId\":\"not-ready\",\"ProjectName\":\"default\",\"Status\":\"Stopped\"}]", 1, 100, 1)
                : page("[]", 1, 100, 0)));

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertTrue(observation.candidates().isEmpty());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_NO_MODELS", observation.resultCode());
    }

    @Test
    void 预置来源也必须读完自己的所有分页() {
        var calls = new AtomicInteger();
        var presetPages = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            if (isPreset(exchange)) {
                var page = presetPages.incrementAndGet();
                respond(exchange, 200, page("[{\"ModelId\":\"model-" + page
                        + "\",\"ProjectName\":\"default\",\"Status\":\"Running\"}]", page, 1, 2));
            } else {
                respond(exchange, 200, page("[]", 1, 100, 0));
            }
        });

        var observation = discoverer().discover(request());

        assertTrue(observation.succeeded());
        assertEquals(2, observation.candidates().size());
        assertEquals(3, calls.get());
        assertEquals(2, presetPages.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ModelId\":\"m\",\"ProjectName\":\"other\",\"Status\":\"Running\"}",
            "{\"ModelId\":\"m\",\"Status\":\"Running\"}",
            "{\"Id\":\"ep-m-default:m\",\"ProjectName\":\"default\",\"Status\":\"Running\"}",
            "{\"ModelId\":12,\"ProjectName\":\"default\",\"Status\":\"Running\"}",
            "{\"ModelId\":\" \",\"ProjectName\":\"default\",\"Status\":\"Running\"}",
            "{\"ModelId\":\"m\",\"ProjectName\":\"default\"}",
            "{\"ModelId\":\"m\",\"ProjectName\":\"default\",\"Status\":false}",
            "null"
    })
    void 预置项目或必需字段不合法时必须拒绝整次发现(String item) {
        server.createContext("/", exchange -> respond(exchange, 200, isPreset(exchange)
                ? page("[" + item + "]", 1, 100, 1)
                : page("[{\"Id\":\"ep-existing\",\"ProjectName\":\"default\"}]", 1, 100, 1)));

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertTrue(observation.candidates().isEmpty());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID", observation.resultCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Id\":\"ep-custom\",\"ProjectName\":\"other\"}",
            "{\"Id\":\"ep-custom\"}",
            "{\"ProjectName\":\"default\"}",
            "{\"Id\":\" \",\"ProjectName\":\"default\"}"
    })
    void 自定义来源同样必须校验default与官方Id(String item) {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, page("[" + item + "]", 1, 100, 1));
        });

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID", observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @CsvSource({
            "401,VOLCENGINE_ARK_DISCOVERY_AUTHENTICATION_FAILED",
            "403,VOLCENGINE_ARK_DISCOVERY_AUTHENTICATION_FAILED",
            "429,VOLCENGINE_ARK_DISCOVERY_RATE_LIMITED",
            "503,VOLCENGINE_ARK_DISCOVERY_CALL_FAILED"
    })
    void 预置读取失败时不能提交已读自定义候选且必须释放独立凭据(int status, String code) {
        var calls = new AtomicInteger();
        var secrets = new ArrayList<SecretValue>();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            respond(exchange, isPreset(exchange) ? status : 200, isPreset(exchange)
                    ? "{\"ResponseMetadata\":{\"Error\":{\"Message\":\"TEST_DISCOVERY_CREDENTIAL\"}}}"
                    : page("[{\"Id\":\"ep-custom\",\"ProjectName\":\"default\"}]", 1, 100, 1));
        });
        var discoverer = new VolcengineArkPlatformProviderModelDiscoverer(
                reference -> {
                    assertFalse(reference.contains("platform-provider-connection/"));
                    var secret = SecretValue.copyOf("TEST_DISCOVERY_CREDENTIAL".toCharArray());
                    secrets.add(secret);
                    return secret;
                }, endpoint -> {}, Clock.fixed(NOW, ZoneOffset.UTC),
                region -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

        var observation = discoverer.discover(request());

        assertFalse(observation.succeeded());
        assertEquals(code, observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(2, calls.get());
        assertEquals(2, secrets.size());
        secrets.forEach(secret -> assertThrows(IllegalStateException.class, () -> secret.use(chars -> true)));
    }

    @Test
    void 两来源必须共享分页请求上限() {
        var calls = new AtomicInteger();
        var presetPages = new AtomicInteger();
        server.createContext("/", exchange -> {
            int call = calls.incrementAndGet();
            boolean preset = isPreset(exchange);
            int page = preset ? presetPages.incrementAndGet() : call;
            respond(exchange, 200, preset
                    ? page("[{\"ModelId\":\"preset\",\"ProjectName\":\"default\",\"Status\":\"Running\"}]", page, 1, 2)
                    : page("[{\"Id\":\"ep-custom\",\"ProjectName\":\"default\"}]", page, 1, 99));
        });

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID", observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(100, calls.get());
        assertEquals(1, presetPages.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Items\":[],\"PageNumber\":1,\"PageSize\":100,\"TotalCount\":1}",
            "{\"Items\":[],\"PageNumber\":0,\"PageSize\":100,\"TotalCount\":0}",
            "{\"Items\":[],\"PageNumber\":1,\"PageSize\":0,\"TotalCount\":0}",
            "{\"Items\":[],\"PageNumber\":1,\"PageSize\":101,\"TotalCount\":0}",
            "{\"Items\":[],\"PageNumber\":1,\"PageSize\":100,\"TotalCount\":-1}",
            "{\"Items\":[],\"PageNumber\":1,\"PageSize\":100,\"TotalCount\":10001}",
            "{\"Items\":[],\"PageNumber\":1.5,\"PageSize\":100,\"TotalCount\":0}"
    })
    void 不完整或超限分页必须在继续请求前拒绝(String result) {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"ResponseMetadata\":{},\"Result\":" + result + "}");
        });

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID", observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void 分页过程中总数变化不能形成不完整成功发现() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            int page = calls.incrementAndGet();
            respond(exchange, 200, page("[{\"Id\":\"ep-custom\",\"ProjectName\":\"default\"}]",
                    page, 1, page == 1 ? 2 : 3));
        });

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID", observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void 分页过程中页大小变化不能跳过未读记录() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            int page = calls.incrementAndGet();
            respond(exchange, 200, page(page == 1
                    ? "[{\"Id\":\"ep-first\",\"ProjectName\":\"default\"}]"
                    : "[{\"Id\":\"ep-third\",\"ProjectName\":\"default\"},"
                            + "{\"Id\":\"ep-fourth\",\"ProjectName\":\"default\"}]", page, page == 1 ? 1 : 2, 4));
        });

        var observation = discoverer().discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_RESPONSE_INVALID", observation.resultCode());
        assertTrue(observation.candidates().isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void 预置请求前出站策略拒绝时不能外呼或返回部分候选() {
        var calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, page("[{\"Id\":\"ep-custom\",\"ProjectName\":\"default\"}]", 1, 100, 1));
        });
        var discoverer = new VolcengineArkPlatformProviderModelDiscoverer(
                reference -> SecretValue.copyOf("TEST_DISCOVERY_CREDENTIAL".toCharArray()),
                endpoint -> {
                    if (endpoint.getRawQuery() != null && endpoint.getRawQuery().contains("InnerDescribeModelEndpoints")) {
                        throw new IllegalArgumentException("出站拒绝");
                    }
                }, Clock.fixed(NOW, ZoneOffset.UTC),
                region -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

        var observation = discoverer.discover(request());

        assertFalse(observation.succeeded());
        assertEquals("VOLCENGINE_ARK_DISCOVERY_ENDPOINT_REJECTED", observation.resultCode());
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

    private VolcengineArkPlatformProviderModelDiscoverer discoverer() {
        return new VolcengineArkPlatformProviderModelDiscoverer(
                reference -> SecretValue.copyOf("TEST_DISCOVERY_CREDENTIAL".toCharArray()),
                endpoint -> {}, Clock.fixed(NOW, ZoneOffset.UTC),
                region -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    private static boolean isPreset(HttpExchange exchange) {
        return exchange.getRequestURI().getRawQuery().equals("Action=InnerDescribeModelEndpoints&Version=2024-01-01");
    }

    private static String page(String items, int page, int size, int total) {
        return "{\"ResponseMetadata\":{},\"Result\":{\"Items\":" + items
                + ",\"PageNumber\":" + page + ",\"PageSize\":" + size + ",\"TotalCount\":" + total + "}}";
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
