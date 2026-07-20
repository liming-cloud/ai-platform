package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModelProviderKindsTest {

    @ParameterizedTest
    @MethodSource("runtimeAliases")
    void canonicalizesEveryAliasAlreadyAcceptedByRuntime(
            String alias,
            String canonicalKind,
            String requiredAuthType
    ) {
        assertThat(ModelProviderKinds.canonicalize(alias)).isEqualTo(canonicalKind);
        assertThat(ModelProviderKinds.requiredAuthType(alias)).isEqualTo(requiredAuthType);
        assertThat(new ModelRuntimeOptions(java.util.Map.of("providerKind", alias)).providerKind())
                .isEqualTo(canonicalKind);
    }

    @Test
    void acceptsLegacyVertexMigrationValueForCanonicalVertexProvider() {
        assertThatCode(() -> ModelProviderKinds.requireCompatibleRuntimeKind("vertex-ai", "vertexai"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossFamilyRuntimeAliasAfterCanonicalization() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ModelProviderKinds.requireCompatibleRuntimeKind("azure_openai", "openai")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须与供应商类型 azure-openai 一致");
    }

    private static Stream<Arguments> runtimeAliases() {
        return Stream.of(
                Arguments.of("openai-compatible", "openai-compatible", "api_key"),
                Arguments.of("openai", "openai-compatible", "api_key"),
                Arguments.of("azure-openai", "azure-openai", "api_key"),
                Arguments.of("azure_openai", "azure-openai", "api_key"),
                Arguments.of("azureopenai", "azure-openai", "api_key"),
                Arguments.of("ollama", "ollama", "no_auth"),
                Arguments.of("mistral-ai", "mistral-ai", "api_key"),
                Arguments.of("mistral_ai", "mistral-ai", "api_key"),
                Arguments.of("mistral", "mistral-ai", "api_key"),
                Arguments.of("bedrock", "bedrock", "aws_credentials"),
                Arguments.of("amazon-bedrock", "bedrock", "aws_credentials"),
                Arguments.of("amazon_bedrock", "bedrock", "aws_credentials"),
                Arguments.of("aws-bedrock", "bedrock", "aws_credentials"),
                Arguments.of("aws_bedrock", "bedrock", "aws_credentials"),
                Arguments.of("vertex-ai", "vertex-ai", "gcp_service_account"),
                Arguments.of("vertex_ai", "vertex-ai", "gcp_service_account"),
                Arguments.of("vertexai", "vertex-ai", "gcp_service_account"),
                Arguments.of("google-vertex-ai", "vertex-ai", "gcp_service_account"),
                Arguments.of("google_vertex_ai", "vertex-ai", "gcp_service_account"),
                Arguments.of("gcp-vertex-ai", "vertex-ai", "gcp_service_account"),
                Arguments.of("gcp_vertex_ai", "vertex-ai", "gcp_service_account"),
                Arguments.of("local-deterministic", "local-deterministic", "no_auth")
        );
    }
}
