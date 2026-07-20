package com.aiplatform.infra.springai.embedding.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.aiplatform.infra.springai.embedding.model.EmbeddingRuntimeRequest;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.ollama.strategy.OllamaSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.openai.OpenAiCompatibleSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;

class SpringAiRuntimeEmbeddingClientAdapterTest {
    private static final String OPTIONS_DIGEST = "a".repeat(64);

    @Test
    void resolvesSecretRefAndCreatesEmbeddingModelFromRuntimeRequest() {
        RecordingEmbeddingModelFactory factory = new RecordingEmbeddingModelFactory(new float[] {0.1f, 0.2f});
        SpringAiRuntimeEmbeddingClientAdapter adapter = new SpringAiRuntimeEmbeddingClientAdapter(
                optionsRegistry(),
                List.of(factory),
                request -> {
                    assertThat(request).isEqualTo(new SpringAiRuntimeSecretResolutionRequest(
                            "2001", "4001", "3001", "silicon-flow", "openai-compatible",
                            "https://api.siliconflow.cn/v1", "api_key", "BAAI/bge-m3",
                            7, "EMBEDDING", Map.of("dimensions", 1536), OPTIONS_DIGEST, "9001"
                    ));
                    return "sk-live-secret";
                }
        );

        float[] embedding = adapter.embed(runtimeRequest(), "制度内容");

        assertThat(factory.capturedRequest.endpoint()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(factory.capturedRequest.credentialMaterial()).isEqualTo("sk-live-secret");
        assertThat(factory.capturedRequest.modelType()).isEqualTo("EMBEDDING");
        assertThat(embedding).containsExactly(0.1f, 0.2f);
    }

    @Test
    void cloudCredentialAuthTypeRequiresCredentialRef() {
        EmbeddingRuntimeRequest request = new EmbeddingRuntimeRequest(
                "2001",
                "4003",
                "3003",
                "aws-bedrock",
                "bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "AWS_CREDENTIALS",
                "9101",
                "amazon.titan-embed-text-v2:0",
                7,
                "EMBEDDING",
                Map.of("dimensions", 1024),
                OPTIONS_DIGEST,
                "policy-v3"
        );

        assertThat(request.authType()).isEqualTo("aws_credentials");
        assertThat(request.secretRefId()).isEqualTo("9101");
        assertThat(request.requiresCredentialMaterial()).isTrue();
    }

    @Test
    void noAuthProviderStillResolvesAuthorityBeforeCreatingEmbeddingModel() {
        RecordingEmbeddingModelFactory factory = new RecordingEmbeddingModelFactory(new float[] {0.3f, 0.4f});
        SpringAiRuntimeEmbeddingClientAdapter adapter = new SpringAiRuntimeEmbeddingClientAdapter(
                optionsRegistry(),
                List.of(factory),
                request -> {
                    assertThat(request.providerId()).isEqualTo("3002");
                    assertThat(request.authType()).isEqualTo("no_auth");
                    assertThat(request.secretRefId()).isNull();
                    return null;
                }
        );

        float[] embedding = adapter.embed(noAuthRuntimeRequest(), "制度内容");

        assertThat(factory.capturedRequest.authType()).isEqualTo("no_auth");
        assertThat(factory.capturedRequest.credentialMaterial()).isNull();
        assertThat(embedding).containsExactly(0.3f, 0.4f);
    }

    @Test
    void resolverRejectionPreventsEmbeddingModelCreationAndNetworkCall() {
        RecordingEmbeddingModelFactory factory = new RecordingEmbeddingModelFactory(new float[] {0.3f, 0.4f});
        SpringAiRuntimeEmbeddingClientAdapter adapter = new SpringAiRuntimeEmbeddingClientAdapter(
                optionsRegistry(),
                List.of(factory),
                request -> {
                    throw new IllegalStateException("模型运行时 authority 校验失败");
                }
        );

        assertThatThrownBy(() -> adapter.embed(noAuthRuntimeRequest(), "制度内容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authority 校验失败");
        assertThat(factory.capturedRequest).isNull();
    }

    @Test
    void rejectsDamagedEmbeddingFactoryListInsteadOfJdkNullPointerException() {
        ArrayList<SpringAiEmbeddingModelFactory> factories = new ArrayList<>();
        factories.add(null);

        assertThatThrownBy(() -> new SpringAiRuntimeEmbeddingClientAdapter(
                optionsRegistry(),
                factories,
                request -> "sk-live-secret"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EmbeddingModel 工厂不能为空");
    }

    @Test
    void rejectsNonFiniteEmbeddingDimensionOptionValues() {
        EmbeddingRuntimeRequest request = runtimeRequest(Map.of("dimensions", Double.NaN));

        assertThatThrownBy(() -> request.optionAsInteger("dimensions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions 必须是整数");
    }

    @Test
    void rejectsFractionalEmbeddingDimensionOptionValues() {
        EmbeddingRuntimeRequest request = runtimeRequest(Map.of("dimensions", 1536.5d));

        assertThatThrownBy(() -> request.optionAsInteger("dimensions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions 必须是整数");
    }

    @Test
    void rejectsDamagedEmbeddingRuntimeOptionsInsteadOfJdkNullPointerException() {
        Map<String, Object> optionsWithNullValue = new LinkedHashMap<>();
        optionsWithNullValue.put("dimensions", null);
        Map<String, Object> optionsWithNullKey = new LinkedHashMap<>();
        optionsWithNullKey.put(null, 1536);

        assertThatThrownBy(() -> runtimeRequest(optionsWithNullValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
        assertThatThrownBy(() -> runtimeRequest(optionsWithNullKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options key不能为空");
    }

    @Test
    void rejectsEmptyEmbeddingVectorFromRuntimeModel() {
        SpringAiRuntimeEmbeddingClientAdapter adapter = adapterReturning(new float[0]);

        assertThatThrownBy(() -> adapter.embed(runtimeRequest(), "制度内容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding 模型返回空向量");
    }

    @Test
    void rejectsNonFiniteEmbeddingVectorValuesFromRuntimeModel() {
        SpringAiRuntimeEmbeddingClientAdapter adapter = adapterReturning(new float[] {0.1f, Float.NaN});

        assertThatThrownBy(() -> adapter.embed(runtimeRequest(), "制度内容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding 模型返回非法向量值");
    }

    @Test
    void redactsEncodedCredentialAndDoesNotRetainRawProviderExceptionAsCause() {
        String credentialMaterial = "Tenant-Credential+/ secret";
        String encodedCredential = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(credentialMaterial.getBytes(StandardCharsets.UTF_8));
        SpringAiEmbeddingModelFactory throwingFactory = new SpringAiEmbeddingModelFactory() {
            @Override
            public boolean supports(SpringAiModelRuntimeRequest request) {
                return true;
            }

            @Override
            public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
                throw new IllegalStateException(
                        "request failed Authorization%3A%20Bearer%20" + encodedCredential
                );
            }
        };
        SpringAiRuntimeEmbeddingClientAdapter adapter = new SpringAiRuntimeEmbeddingClientAdapter(
                optionsRegistry(),
                List.of(throwingFactory),
                request -> credentialMaterial
        );

        Throwable failure = catchThrowable(() -> adapter.embed(runtimeRequest(), "制度内容"));

        assertThat(failure)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding 模型调用失败：request failed Authorization: Bearer ***")
                .hasMessageNotContaining(credentialMaterial)
                .hasMessageNotContaining(encodedCredential);
        assertThat(failure.getCause()).isNull();
    }

    private static SpringAiModelOptionsRegistry optionsRegistry() {
        return new SpringAiModelOptionsRegistry(List.of(
                new OpenAiCompatibleSpringAiModelOptionsStrategy(),
                new OllamaSpringAiModelOptionsStrategy(),
                new StandardSpringAiModelOptionsStrategy()
        ));
    }

    private static SpringAiRuntimeEmbeddingClientAdapter adapterReturning(float[] response) {
        return new SpringAiRuntimeEmbeddingClientAdapter(
                optionsRegistry(),
                List.of(new RecordingEmbeddingModelFactory(response)),
                request -> "sk-live-secret"
        );
    }

    private static EmbeddingRuntimeRequest runtimeRequest() {
        return runtimeRequest(Map.of("dimensions", 1536));
    }

    private static EmbeddingRuntimeRequest runtimeRequest(Map<String, Object> effectiveOptions) {
        return new EmbeddingRuntimeRequest(
                "2001",
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                "https://api.siliconflow.cn/v1",
                "api_key",
                "9001",
                "BAAI/bge-m3",
                7,
                "EMBEDDING",
                effectiveOptions,
                OPTIONS_DIGEST,
                "policy-v1"
        );
    }

    private static EmbeddingRuntimeRequest noAuthRuntimeRequest() {
        return new EmbeddingRuntimeRequest(
                "2001",
                "4002",
                "3002",
                "local-ollama",
                "ollama",
                "http://localhost:11434",
                "no_auth",
                null,
                "nomic-embed-text",
                7,
                "EMBEDDING",
                Map.of("dimensions", 768),
                OPTIONS_DIGEST,
                "policy-v2"
        );
    }
}
