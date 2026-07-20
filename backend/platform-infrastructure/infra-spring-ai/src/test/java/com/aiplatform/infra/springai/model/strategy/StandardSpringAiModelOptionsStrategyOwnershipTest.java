package com.aiplatform.infra.springai.model.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.model.provider.vertexai.strategy.VertexAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StandardSpringAiModelOptionsStrategyOwnershipTest {
    private final StandardSpringAiModelOptionsStrategy standard = new StandardSpringAiModelOptionsStrategy();

    @ParameterizedTest
    @MethodSource("dedicatedProviderKinds")
    void standardFallbackNeverClaimsAProviderOwnedByADedicatedStrategy(String providerKind) {
        assertThat(standard.supports(request(providerKind, "CHAT"))).isFalse();
    }

    @Test
    void standardFallbackRemainsAvailableForUnknownExtensionProviders() {
        assertThat(standard.supports(request("custom-spring-ai-extension", "CHAT"))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("unsupportedVertexChatKinds")
    void vertexChatAndReasoningAliasesCannotFallBackToStandard(
            String providerKind,
            String modelType
    ) {
        SpringAiModelOptionsRegistry registry = new SpringAiModelOptionsRegistry(List.of(
                new StandardSpringAiModelOptionsStrategy(),
                new VertexAiSpringAiModelOptionsStrategy()
        ));

        assertThatThrownBy(() -> registry.select(request(providerKind, modelType)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到支持该模型类型");
    }

    private static Stream<String> dedicatedProviderKinds() {
        return Stream.of(
                "openai-compatible", "openai",
                "azure-openai", "azure_openai", "azureopenai",
                "ollama",
                "mistral-ai", "mistral_ai", "mistral",
                "bedrock", "amazon-bedrock", "amazon_bedrock", "aws-bedrock", "aws_bedrock",
                "vertex-ai", "vertex_ai", "vertexai", "google-vertex-ai", "google_vertex_ai",
                "gcp-vertex-ai", "gcp_vertex_ai",
                "local-deterministic"
        );
    }

    private static Stream<Arguments> unsupportedVertexChatKinds() {
        return Stream.of(
                "vertex-ai", "vertex_ai", "vertexai", "google-vertex-ai", "google_vertex_ai",
                "gcp-vertex-ai", "gcp_vertex_ai"
        ).flatMap(providerKind -> Stream.of("CHAT", "REASONING")
                .map(modelType -> Arguments.of(providerKind, modelType)));
    }

    private static SpringAiModelRuntimeRequest request(String providerKind, String modelType) {
        return new SpringAiModelRuntimeRequest(
                "provider",
                providerKind,
                "https://provider.example/v1",
                "api_key",
                "model",
                modelType,
                8192,
                null,
                Map.of(),
                null
        );
    }
}
