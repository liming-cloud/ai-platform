package com.aiplatform.infra.springai.model.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.provider.ollama.strategy.OllamaSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import com.aiplatform.infra.springai.test.TestModelHttpTransport;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

class OllamaSpringAiModelProbeClientTest {
    private final SecureModelHttpTransport transport = TestModelHttpTransport.permissiveLocalTransport();
    private final OllamaSpringAiModelOptionsStrategy optionsStrategy = new OllamaSpringAiModelOptionsStrategy();
    private final OllamaSpringAiModelProbeClient probeClient = new OllamaSpringAiModelProbeClient(
            new OllamaChatModelFactory(transport, new RetryTemplate(RetryPolicy.withMaxRetries(0))),
            new OllamaEmbeddingModelFactory(transport)
    );

    @AfterEach
    void closeTransport() {
        transport.close();
    }

    @Test
    void springSelectsTheProductionConstructorWhenTheRetrySeamAlsoExists() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    SecureModelHttpTransport.class,
                    () -> mock(SecureModelHttpTransport.class)
            );
            context.register(OllamaChatModelFactory.class);

            context.refresh();

            assertThat(context.getBean(OllamaChatModelFactory.class)).isNotNull();
        }
    }

    @Test
    void probesChatModelThroughSpringAiOllamaChatModel() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = chatServer(capturedPath, capturedBody);
        try {
            SpringAiModelRuntimeRequest request = chatRequest(endpoint(server));

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(result.message()).contains("Ollama 模型运行时探测通过");
            assertThat(capturedPath.get()).isEqualTo("/api/chat");
            assertThat(capturedBody.get()).contains("\"model\":\"llama3.1\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void probesEmbeddingModelThroughSpringAiOllamaEmbeddingModel() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = embeddingServer(capturedPath, capturedBody);
        try {
            SpringAiModelRuntimeRequest request = embeddingRequest(endpoint(server));

            SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                    request,
                    optionsStrategy.buildOptions(request)
            ));

            assertThat(result.connected()).isTrue();
            assertThat(result.message()).contains("Ollama 模型运行时探测通过");
            assertThat(capturedPath.get()).isEqualTo("/api/embed");
            assertThat(capturedBody.get()).contains("\"model\":\"nomic-embed-text\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsApiKeyAuthType() {
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "local-ollama",
                "ollama",
                "http://127.0.0.1:11434",
                "api_key",
                "llama3.1",
                "CHAT",
                8192,
                null,
                Map.of("temperature", 0.2),
                "sk-not-allowed"
        );

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(chatRequest("http://127.0.0.1:11434"))
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("Ollama 模型供应商仅支持 no_auth 鉴权");
    }

    @Test
    void returnsChineseNetworkFailure() {
        SpringAiModelRuntimeRequest request = chatRequest("http://127.0.0.1:9");

        SpringAiModelConnectivityResult result = probeClient.probe(new SpringAiModelRuntimeProbe(
                request,
                optionsStrategy.buildOptions(request)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("Ollama 运行时网络连接失败");
    }

    private static SpringAiModelRuntimeRequest chatRequest(String endpoint) {
        return new SpringAiModelRuntimeRequest(
                "local-ollama",
                "ollama",
                endpoint,
                "no_auth",
                "llama3.1",
                "CHAT",
                8192,
                null,
                Map.of("temperature", 0.2, "maxTokens", 1),
                null
        );
    }

    private static SpringAiModelRuntimeRequest embeddingRequest(String endpoint) {
        return new SpringAiModelRuntimeRequest(
                "local-ollama",
                "ollama",
                endpoint,
                "no_auth",
                "nomic-embed-text",
                "EMBEDDING",
                8192,
                768,
                Map.of("dimensions", 768),
                null
        );
    }

    private static HttpServer chatServer(
            AtomicReference<String> capturedPath,
            AtomicReference<String> capturedBody
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/chat", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "model": "llama3.1",
                      "created_at": "2026-05-25T08:00:00Z",
                      "message": { "role": "assistant", "content": "pong" },
                      "done": true,
                      "done_reason": "stop",
                      "total_duration": 1,
                      "load_duration": 1,
                      "prompt_eval_count": 1,
                      "prompt_eval_duration": 1,
                      "eval_count": 1,
                      "eval_duration": 1
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
        server.createContext("/api/embed", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "model": "nomic-embed-text",
                      "embeddings": [[0.1, 0.2, 0.3]],
                      "total_duration": 1,
                      "load_duration": 1,
                      "prompt_eval_count": 1
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
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
