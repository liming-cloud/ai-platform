package com.aiplatform.infra.springai.model.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import com.aiplatform.infra.springai.test.TestModelHttpTransport;
import com.aiplatform.shared.security.net.StrictOutboundEndpointPolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

class OpenAiCompatibleSpringAiModelProbeClientTest {
    private final SecureModelHttpTransport transport = TestModelHttpTransport.permissiveLocalTransport();
    private final OpenAiCompatibleSpringAiModelProbeClient probeClient = new OpenAiCompatibleSpringAiModelProbeClient(
            new OpenAiCompatibleChatModelFactory(transport),
            new OpenAiCompatibleEmbeddingModelFactory(transport)
    );
    private final OpenAiCompatibleSpringAiModelOptionsStrategy optionsStrategy =
            new OpenAiCompatibleSpringAiModelOptionsStrategy();

    @AfterEach
    void closeTransport() {
        transport.close();
    }

    @Test
    void probesChatModelThroughSpringAiChatModel() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = chatCompletionServer(capturedPath, capturedBody);
        try {
            SpringAiModelRuntimeRequest request = request(endpoint(server), "sk-test-secret");

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(result.message()).contains("Spring AI 模型抽象");
            assertThat(capturedPath.get()).isEqualTo("/v1/chat/completions");
            assertThat(capturedBody.get()).contains("\"model\":\"gpt-4.1-mini\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void probesEmbeddingModelThroughSpringAiEmbeddingModel() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = embeddingServer(capturedPath, capturedBody);
        try {
            SpringAiModelRuntimeRequest request = embeddingRequest(endpoint(server), "***");

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(result.message()).contains("Spring AI 模型抽象");
            assertThat(capturedPath.get()).isEqualTo("/v1/embeddings");
            assertThat(capturedBody.get()).contains("\"model\":\"BAAI/bge-m3\"");
            assertThat(capturedBody.get()).contains("\"dimensions\":3");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void embeddingProbeDoesNotLeakMetadataOnlyDimensionToOpenAiCompatibleProvider() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = embeddingServer(new AtomicReference<>(), capturedBody);
        try {
            SpringAiModelRuntimeRequest request = embeddingRequestWithoutDimensionOption(endpoint(server), "***");

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(capturedBody.get()).contains("\"model\":\"BAAI/bge-m3\"");
            assertThat(capturedBody.get()).doesNotContain("\"dimensions\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsChineseFailureAndRedactsApiKeyWhenSpringAiCallFails() {
        SpringAiModelRuntimeRequest request = request("http://127.0.0.1:9/v1", "sk-test-secret");

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("模型供应商网络连接失败");
        assertThat(result.message()).doesNotContain("sk-test-secret");
    }

    @Test
    void classifiesTlsHandshakeEofCauseChainAsNetworkFailure() {
        SSLHandshakeException handshakeFailure = new SSLHandshakeException("Remote host terminated the handshake");
        handshakeFailure.initCause(new EOFException("SSL peer shut down incorrectly"));
        OpenAiCompatibleChatModelFactory failingFactory = new OpenAiCompatibleChatModelFactory(transport) {
            @Override
            public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
                throw new IllegalStateException("provider probe failed", handshakeFailure);
            }
        };
        OpenAiCompatibleSpringAiModelProbeClient client = new OpenAiCompatibleSpringAiModelProbeClient(
                failingFactory,
                new OpenAiCompatibleEmbeddingModelFactory(transport)
        );
        SpringAiModelRuntimeRequest request = request("https://provider.example/v1", "sk-test-secret");

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).startsWith("模型供应商网络连接失败：");
        assertThat(result.message()).doesNotContain("sk-test-secret");
    }

    @Test
    void failsClosedWhenApiKeyIsMissing() {
        SpringAiModelRuntimeRequest request = request("http://127.0.0.1:9/v1", null);

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("OpenAI-compatible 模型供应商密钥未配置");
    }

    @Test
    void failsClosedWhenAuthTypeIsNoAuth() {
        SpringAiModelRuntimeRequest request = requestWithAuthType("http://127.0.0.1:9/v1", "no_auth", null);

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("OpenAI-compatible 模型供应商仅支持 api_key 鉴权");
    }

    @Test
    void rejectsLegacyPrivateEndpointForBothChatAndEmbeddingBeforeNetworkIo() {
        StrictOutboundEndpointPolicy privateAddressPolicy = new StrictOutboundEndpointPolicy(host ->
                new InetAddress[]{InetAddress.getByName("10.0.0.8")}
        );
        try (SecureModelHttpTransport strictTransport = new SecureModelHttpTransport(privateAddressPolicy)) {
            SpringAiModelRuntimeRequest chat = request("https://provider.example/v1", "sk-test-secret");
            SpringAiModelRuntimeRequest embedding = embeddingRequest("https://provider.example/v1", "sk-test-secret");

            assertThatThrownBy(() -> new OpenAiCompatibleChatModelFactory(strictTransport)
                    .create(chat, (org.springframework.ai.chat.prompt.ChatOptions) optionsStrategy.buildOptions(chat)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非公网地址");
            assertThatThrownBy(() -> new OpenAiCompatibleEmbeddingModelFactory(strictTransport)
                    .create(embedding, (org.springframework.ai.embedding.EmbeddingOptions)
                            optionsStrategy.buildOptions(embedding)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非公网地址");
        }
    }

    private static SpringAiModelRuntimeRequest request(String endpoint, String apiKey) {
        return requestWithAuthType(endpoint, "api_key", apiKey);
    }

    private static SpringAiModelRuntimeRequest requestWithAuthType(String endpoint, String authType, String apiKey) {
        return new SpringAiModelRuntimeRequest(
                "openai",
                "openai-compatible",
                endpoint,
                authType,
                "gpt-4.1-mini",
                "CHAT",
                128000,
                null,
                Map.of("temperature", 0.2, "maxTokens", 1),
                apiKey
        );
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(String endpoint, String apiKey) {
        return new SpringAiModelRuntimeRequest(
                "openai",
                "openai-compatible",
                endpoint,
                "api_key",
                "BAAI/bge-m3",
                "EMBEDDING",
                8192,
                3,
                Map.of("dimensions", 3),
                apiKey
        );
    }

    private static SpringAiModelRuntimeRequest embeddingRequestWithoutDimensionOption(String endpoint, String apiKey) {
        return new SpringAiModelRuntimeRequest(
                "openai",
                "openai-compatible",
                endpoint,
                "api_key",
                "BAAI/bge-m3",
                "EMBEDDING",
                8192,
                1024,
                Map.of(),
                apiKey
        );
    }

    private static HttpServer chatCompletionServer(
            AtomicReference<String> capturedPath,
            AtomicReference<String> capturedBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "created": 1710000000,
                      "model": "gpt-4.1-mini",
                      "choices": [
                        {
                          "index": 0,
                          "message": { "role": "assistant", "content": "pong" },
                          "finish_reason": "stop"
                        }
                      ]
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
            AtomicReference<String> capturedBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "object": "list",
                      "model": "BAAI/bge-m3",
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
