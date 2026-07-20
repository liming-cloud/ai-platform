package com.aiplatform.infra.springai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.provider.openai.OpenAiCompatibleSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.ollama.strategy.OllamaSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

class SpringAiModelRuntimeGatewayTest {
    @Test
    void validatesAndBuildsChatOptions() {
        SpringAiModelRuntimeGateway gateway = gateway();
        SpringAiModelRuntimeRequest request = request("CHAT", null, Map.of(
                "temperature", 0.2,
                "topP", 0.8,
                "maxTokens", 1024
        ));

        SpringAiModelValidationResult validation = gateway.validate(request);
        ModelOptions options = gateway.buildOptions(request);

        assertThat(validation.valid()).isTrue();
        assertThat(options).isInstanceOf(ChatOptions.class);
        ChatOptions chatOptions = (ChatOptions) options;
        assertThat(chatOptions.getModel()).isEqualTo("gpt-4.1-mini");
        assertThat(chatOptions.getTemperature()).isEqualTo(0.2);
        assertThat(chatOptions.getTopP()).isEqualTo(0.8);
        assertThat(chatOptions.getMaxTokens()).isEqualTo(1024);
    }

    @Test
    void validatesAndBuildsEmbeddingOptions() {
        SpringAiModelRuntimeGateway gateway = gateway();
        SpringAiModelRuntimeRequest request = request("EMBEDDING", 1536, Map.of("dimensions", 1536));

        ModelOptions options = gateway.buildOptions(request);

        assertThat(options).isInstanceOf(EmbeddingOptions.class);
        EmbeddingOptions embeddingOptions = (EmbeddingOptions) options;
        assertThat(embeddingOptions.getModel()).isEqualTo("gpt-4.1-mini");
        assertThat(embeddingOptions.getDimensions()).isEqualTo(1536);
    }

    @Test
    void embeddingMetadataDimensionDoesNotLeakIntoProviderRequestOptions() {
        SpringAiModelRuntimeGateway gateway = gateway();
        SpringAiModelRuntimeRequest request = request("EMBEDDING", 1024, Map.of());

        ModelOptions options = gateway.buildOptions(request);

        assertThat(options).isInstanceOf(EmbeddingOptions.class);
        EmbeddingOptions embeddingOptions = (EmbeddingOptions) options;
        assertThat(embeddingOptions.getModel()).isEqualTo("gpt-4.1-mini");
        assertThat(embeddingOptions.getDimensions()).isNull();
    }

    @Test
    void rejectsUnknownOptionKeys() {
        SpringAiModelRuntimeGateway gateway = gateway();
        SpringAiModelValidationResult validation = gateway.validate(request("CHAT", null, Map.of("vendorMagic", true)));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.summary()).contains("不支持的模型 options 字段 vendorMagic");
    }

    @Test
    void rejectsNonFiniteDoubleOptionValues() {
        SpringAiModelRuntimeGateway gateway = gateway();

        SpringAiModelValidationResult validation = gateway.validate(request("CHAT", null, Map.of("temperature", "NaN")));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.summary()).contains("temperature 必须是数字");
    }

    @Test
    void rejectsFractionalIntegerOptionValues() {
        SpringAiModelRuntimeGateway gateway = gateway();

        SpringAiModelValidationResult validation = gateway.validate(request("CHAT", null, Map.of("maxTokens", 1.5)));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.summary()).contains("maxTokens 必须是整数");
    }

    @Test
    void connectivityDoesNotApplyApiKeyRuleBeforeProviderSelection() {
        SpringAiModelRuntimeGateway gateway = new SpringAiModelRuntimeGateway(
                new SpringAiModelOptionsRegistry(List.of(new OllamaSpringAiModelOptionsStrategy())),
                List.of()
        );

        SpringAiModelConnectivityResult result = gateway.testConnectivity(new SpringAiModelRuntimeRequest(
                "local-ollama",
                "ollama",
                "http://localhost:11434",
                "no_auth",
                "nomic-embed-text",
                "EMBEDDING",
                8192,
                768,
                Map.of("dimensions", 768),
                null
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).contains("暂不支持供应商类型：ollama");
        assertThat(result.message()).doesNotContain("密钥未配置");
    }

    @Test
    void rejectsDamagedProbeClientListInsteadOfJdkNullPointerException() {
        ArrayList<SpringAiModelProbeClient> probeClients = new ArrayList<>();
        probeClients.add(null);

        assertThatThrownBy(() -> new SpringAiModelRuntimeGateway(
                new SpringAiModelOptionsRegistry(List.of(new StandardSpringAiModelOptionsStrategy())),
                probeClients
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spring AI 模型探测客户端不能为空");
    }

    @Test
    void connectivityDelegatesToSpringAiProbeClient() {
        AtomicReference<SpringAiModelRuntimeProbe> capturedProbe = new AtomicReference<>();
        SpringAiModelRuntimeGateway gateway = gateway(probe -> {
            capturedProbe.set(probe);
            return SpringAiModelConnectivityResult.passed("模型运行时探测通过，已通过 Spring AI 模型抽象完成调用");
        });

        SpringAiModelConnectivityResult result = gateway.testConnectivity(requestWithEndpoint(
                "CHAT",
                "https://api.openai.example/v1",
                "sk-live",
                Map.of("temperature", 0.2)
        ));

        assertThat(result.connected()).isTrue();
        assertThat(result.message()).contains("Spring AI 模型抽象");
        assertThat(capturedProbe.get()).isNotNull();
        assertThat(capturedProbe.get().request().modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(capturedProbe.get().options()).isInstanceOf(ChatOptions.class);
    }

    @Test
    void connectivityFailsClosedWhenProviderKindIsUnsupported() {
        SpringAiModelRuntimeGateway gateway = gateway(probe -> {
            throw new AssertionError("不支持的 providerKind 不应访问模型供应商");
        });

        SpringAiModelConnectivityResult result = gateway.testConnectivity(new SpringAiModelRuntimeRequest(
                "custom-provider",
                "unknown-provider",
                "https://api.openai.example/v1",
                "api_key",
                "gpt-4.1-mini",
                "CHAT",
                128000,
                null,
                Map.of(),
                "sk-live"
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("暂不支持供应商类型：unknown-provider");
    }

    @Test
    void zhiPuRemovalReturnsActionableOpenAiCompatibleMigration() {
        SpringAiModelRuntimeGateway gateway = gateway(probe -> {
            throw new AssertionError("已移除的 zhipuai provider 不应访问模型供应商");
        });

        SpringAiModelValidationResult validation = gateway.validate(new SpringAiModelRuntimeRequest(
                "zhipu",
                "zhipuai",
                "https://open.bigmodel.cn/api/paas/v4",
                "api_key",
                "glm-4-plus",
                "CHAT",
                128000,
                null,
                Map.of(),
                "secret"
        ));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.summary())
                .contains("Spring AI 2.0")
                .contains("openai-compatible")
                .contains("智谱 OpenAI 兼容端点");
    }

    @Test
    void vertexChatRemovalReturnsActionableGoogleGenAiMigration() {
        SpringAiModelRuntimeGateway gateway = gateway(probe -> {
            throw new AssertionError("已移除的 vertex-ai CHAT provider 不应访问模型供应商");
        });

        SpringAiModelValidationResult validation = gateway.validate(new SpringAiModelRuntimeRequest(
                "vertex",
                "vertex-ai",
                "https://us-central1-aiplatform.googleapis.com",
                "gcp_service_account",
                "gemini-2.5-pro",
                "CHAT",
                128000,
                null,
                Map.of("projectId", "project", "location", "us-central1"),
                "credential"
        ));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.summary())
                .contains("Spring AI 2.0")
                .contains("google-genai")
                .contains("Vertex AI CHAT");
    }

    @Test
    void registrySelectsOrderedOllamaOptionsStrategyBeforeStandardStrategy() {
        SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
                new StandardSpringAiModelOptionsStrategy(),
                new OllamaSpringAiModelOptionsStrategy()
        ));
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "local-ollama",
                "ollama",
                "http://localhost:11434",
                "no_auth",
                "nomic-embed-text",
                "EMBEDDING",
                8192,
                768,
                Map.of("dimensions", 768),
                null
        );

        ModelOptions options = registry.select(request).buildOptions(request);

        assertThat(options).isInstanceOf(OllamaEmbeddingOptions.class);
        assertThat(((OllamaEmbeddingOptions) options).getModel()).isEqualTo("nomic-embed-text");
    }

    @Test
    void connectivityDoesNotCallProbeWhenOptionsAreInvalid() {
        SpringAiModelRuntimeGateway gateway = gateway(probe -> {
            throw new AssertionError("options 校验失败时不应访问模型供应商");
        });

        SpringAiModelConnectivityResult result = gateway.testConnectivity(requestWithEndpoint(
                "CHAT",
                "https://api.openai.example/v1",
                "sk-live",
                Map.of("temperature", 3)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("模型 options 校验失败");
    }

    private static SpringAiModelRuntimeGateway gateway() {
        return gateway(probe -> SpringAiModelConnectivityResult.passed("模型运行时探测通过"));
    }

    private static SpringAiModelRuntimeGateway gateway(
            Function<SpringAiModelRuntimeProbe, SpringAiModelConnectivityResult> probe
    ) {
        return gateway(new SpringAiModelProbeClient() {
            @Override
            public boolean supports(SpringAiModelRuntimeProbe request) {
                return "openai-compatible".equals(request.request().providerKind());
            }

            @Override
            public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe request) {
                return probe.apply(request);
            }
        });
    }

    private static SpringAiModelRuntimeGateway gateway(SpringAiModelProbeClient probeClient) {
        return new SpringAiModelRuntimeGateway(new SpringAiModelOptionsRegistry(List.of(
                new OpenAiCompatibleSpringAiModelOptionsStrategy(),
                new StandardSpringAiModelOptionsStrategy()
        )), List.of(probeClient));
    }

    private static SpringAiModelRuntimeRequest request(
            String modelType,
            Integer embeddingDimension,
            Map<String, Object> options
    ) {
        return new SpringAiModelRuntimeRequest(
                "openai",
                "openai-compatible",
                "https://api.openai.example/v1",
                "api_key",
                "gpt-4.1-mini",
                modelType,
                128000,
                embeddingDimension,
                options,
                null
        );
    }

    private static SpringAiModelRuntimeRequest requestWithEndpoint(
            String modelType,
            String endpoint,
            String apiKey,
            Map<String, Object> options
    ) {
        return new SpringAiModelRuntimeRequest(
                "openai",
                "openai-compatible",
                endpoint,
                "api_key",
                "gpt-4.1-mini",
                modelType,
                128000,
                null,
                options,
                apiKey
        );
    }

}
