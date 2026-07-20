package com.aiplatform.infra.springai.model.provider.mistral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.provider.mistral.strategy.MistralAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.test.TestModelHttpTransport;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.ai.model.ModelOptions;

class MistralAiSpringAiModelProbeClientTest {
    private final SecureModelHttpTransport transport = TestModelHttpTransport.permissiveLocalTransport();
    private final MistralAiSpringAiModelOptionsStrategy optionsStrategy =
            new MistralAiSpringAiModelOptionsStrategy();
    private final MistralAiSpringAiModelProbeClient probeClient = new MistralAiSpringAiModelProbeClient(
            new MistralAiChatModelFactory(transport),
            new MistralAiEmbeddingModelFactory(transport)
    );

    @AfterEach
    void closeTransport() {
        transport.close();
    }

    @Test
    void registrySelectsMistralOptionsStrategyBeforeStandardStrategy() {
        SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
                new StandardSpringAiModelOptionsStrategy(),
                optionsStrategy
        ));
        SpringAiModelRuntimeRequest request = chatRequest(
                "http://127.0.0.1:8080/v1",
                Map.of("safePrompt", true, "randomSeed", 7, "maxTokens", 1)
        );

        ModelOptions options = registry.select(request).buildOptions(request);

        assertThat(options).isInstanceOf(MistralAiChatOptions.class);
        MistralAiChatOptions chatOptions = (MistralAiChatOptions) options;
        assertThat(chatOptions.getModel()).isEqualTo("mistral-small-latest");
        assertThat(chatOptions.getSafePrompt()).isTrue();
        assertThat(chatOptions.getRandomSeed()).isEqualTo(7);
    }

    @Test
    void probesChatModelThroughSpringAiMistralChatModel() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = chatServer(capturedPath, capturedAuth, capturedBody);
        try {
            SpringAiModelRuntimeRequest request = chatRequest(
                    endpoint(server),
                    Map.of("temperature", 0.2, "maxTokens", 1, "safePrompt", false)
            );

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(result.message()).contains("Mistral AI 模型运行时探测通过");
            assertThat(capturedPath.get()).isEqualTo("/v1/chat/completions");
            assertThat(capturedAuth.get()).isEqualTo("Bearer sk-mistral-test");
            assertThat(capturedBody.get()).contains("\"model\":\"mistral-small-latest\"");
            assertThat(capturedBody.get()).contains("\"safe_prompt\":false");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void probesEmbeddingModelThroughSpringAiMistralEmbeddingModel() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = embeddingServer(capturedPath, capturedAuth, capturedBody);
        try {
            SpringAiModelRuntimeRequest request = embeddingRequest(endpoint(server));

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(result.message()).contains("Mistral AI 模型运行时探测通过");
            assertThat(capturedPath.get()).isEqualTo("/v1/embeddings");
            assertThat(capturedAuth.get()).isEqualTo("Bearer sk-mistral-test");
            assertThat(capturedBody.get()).contains("\"model\":\"mistral-embed\"");
            assertThat(capturedBody.get()).doesNotContain("dimensions");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesMistralAuthAndPrivateOptions() {
        SpringAiModelRuntimeRequest invalidAuth = new SpringAiModelRuntimeRequest(
                "mistral",
                "mistral_ai",
                "https://api.mistral.ai",
                "no_auth",
                "mistral-small-latest",
                "CHAT",
                32000,
                null,
                Map.of("safePrompt", true),
                null
        );
        SpringAiModelRuntimeRequest invalidOption = chatRequest(
                "https://api.mistral.ai",
                Map.of("topK", 5, "safePrompt", "not-boolean")
        );

        assertThat(optionsStrategy.validate(invalidAuth).summary())
                .contains("Mistral AI provider 必须使用 api_key 鉴权")
                .doesNotContain("Mistral AI provider 必须配置 API Key");
        assertThat(optionsStrategy.validate(invalidOption).summary())
                .contains("Mistral AI 不支持的模型 options 字段 topK")
                .contains("safePrompt 必须是布尔值");
    }

    @Test
    void validatesStructureWithoutCredentialButBuildOptionsFailsClosed() {
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "mistral",
                "mistral-ai",
                "https://api.mistral.ai/v1",
                "api_key",
                "mistral-small-latest",
                "CHAT",
                32000,
                null,
                Map.of("maxTokens", 1024),
                null
        );

        assertThat(optionsStrategy.validate(request).valid()).isTrue();
        assertThatThrownBy(() -> optionsStrategy.buildOptions(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mistral AI provider 必须配置 API Key");
    }

    @Test
    void buildsMistralEmbeddingOptionsAndKeepsDimensionsAsMetadataOnly() {
        SpringAiModelRuntimeRequest request = embeddingRequest("https://api.mistral.ai/v1");

        ModelOptions options = optionsStrategy.buildOptions(request);

        assertThat(options).isInstanceOf(MistralAiEmbeddingOptions.class);
        MistralAiEmbeddingOptions embeddingOptions = (MistralAiEmbeddingOptions) options;
        assertThat(embeddingOptions.getModel()).isEqualTo("mistral-embed");
        assertThat(embeddingOptions.getDimensions()).isNull();
    }

    @Test
    void redactsApiKeyWhenNetworkFails() {
        SpringAiModelRuntimeRequest request = chatRequest(
                "http://127.0.0.1:9/v1",
                Map.of("maxTokens", 1)
        );

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("Mistral AI 网络连接失败");
        assertThat(result.message()).doesNotContain("sk-mistral-test");
    }

    private static SpringAiModelRuntimeRequest chatRequest(String endpoint, Map<String, Object> options) {
        return new SpringAiModelRuntimeRequest(
                "mistral",
                "mistral_ai",
                endpoint,
                "api_key",
                "mistral-small-latest",
                "CHAT",
                32000,
                null,
                options,
                "sk-mistral-test"
        );
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(String endpoint) {
        return new SpringAiModelRuntimeRequest(
                "mistral",
                "mistral-ai",
                endpoint,
                "api_key",
                "mistral-embed",
                "EMBEDDING",
                8192,
                1024,
                Map.of("dimensions", 1024, "encodingFormat", "float"),
                "sk-mistral-test"
        );
    }

    private static HttpServer chatServer(
            AtomicReference<String> capturedPath,
            AtomicReference<String> capturedAuth,
            AtomicReference<String> capturedBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "id": "mistral-chat-test",
                      "object": "chat.completion",
                      "created": 1710000000,
                      "model": "mistral-small-latest",
                      "choices": [
                        {
                          "index": 0,
                          "message": { "role": "assistant", "content": "pong" },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 1,
                        "completion_tokens": 1,
                        "total_tokens": 2
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static HttpServer embeddingServer(
            AtomicReference<String> capturedPath,
            AtomicReference<String> capturedAuth,
            AtomicReference<String> capturedBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "object": "list",
                      "model": "mistral-embed",
                      "data": [
                        {
                          "object": "embedding",
                          "index": 0,
                          "embedding": [0.1, 0.2, 0.3]
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 1,
                        "total_tokens": 1
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }
}
