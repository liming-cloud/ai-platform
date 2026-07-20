package com.aiplatform.infra.springai.model.provider.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.embedding.adapter.SpringAiRuntimeEmbeddingClientAdapter;
import com.aiplatform.infra.springai.embedding.model.EmbeddingRuntimeRequest;
import com.aiplatform.infra.springai.model.SpringAiModelRuntimeGateway;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalDeterministicRuntimeIntegrationTest {
    private final LocalDeterministicSpringAiModelOptionsStrategy optionsStrategy =
            new LocalDeterministicSpringAiModelOptionsStrategy();
    private final LocalDeterministicEmbeddingModelFactory embeddingFactory =
            new LocalDeterministicEmbeddingModelFactory();
    private final LocalDeterministicChatModelFactory chatFactory =
            new LocalDeterministicChatModelFactory();
    private final SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
            new StandardSpringAiModelOptionsStrategy(),
            optionsStrategy
    ));

    @Test
    void gatewaySelectsLocalStrategyAndProbesChatAndEmbeddingModels() {
        LocalDeterministicSpringAiModelProbeClient probeClient =
                new LocalDeterministicSpringAiModelProbeClient(embeddingFactory, chatFactory);
        SpringAiModelRuntimeGateway gateway = new SpringAiModelRuntimeGateway(registry, List.of(probeClient));
        SpringAiModelRuntimeRequest chat = runtimeRequest("CHAT", null, Map.of("maxTokens", 16));
        SpringAiModelRuntimeRequest embedding = runtimeRequest("EMBEDDING", 64, Map.of("dimensions", 64));

        assertThat(registry.select(chat)).isSameAs(optionsStrategy);
        assertThat(gateway.testConnectivity(chat).connected()).isTrue();
        assertThat(gateway.testConnectivity(embedding).connected()).isTrue();
    }

    @Test
    void registryRejectsUnsupportedLocalModelTypeInsteadOfFallingBackToStandardOptions() {
        SpringAiModelRuntimeRequest unsupported = runtimeRequest("REASONING", null, Map.of());

        assertThatThrownBy(() -> registry.select(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到支持该模型类型");
    }

    @Test
    void embeddingAdapterUsesLocalStrategyAndFactoryAfterNoAuthAuthorityResolution() {
        SpringAiRuntimeEmbeddingClientAdapter adapter = new SpringAiRuntimeEmbeddingClientAdapter(
                registry,
                List.of(embeddingFactory),
                resolutionRequest -> null
        );
        EmbeddingRuntimeRequest request = new EmbeddingRuntimeRequest(
                "2001",
                "4001",
                "3001",
                "local",
                "local-deterministic",
                "local://deterministic",
                "no_auth",
                null,
                "local-bge-m3",
                7,
                "EMBEDDING",
                Map.of("dimensions", 64),
                "a".repeat(64),
                "policy-local-v1"
        );

        float[] embedding = adapter.embed(request, "企业制度");

        assertThat(embedding).hasSize(64);
    }

    private static SpringAiModelRuntimeRequest runtimeRequest(
            String modelType,
            Integer dimension,
            Map<String, Object> options
    ) {
        return new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "local://deterministic",
                "no_auth",
                "local-model",
                modelType,
                8192,
                dimension,
                options,
                null
        );
    }
}
