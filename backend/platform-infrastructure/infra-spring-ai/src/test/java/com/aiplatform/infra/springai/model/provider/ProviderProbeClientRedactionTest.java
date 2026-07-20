package com.aiplatform.infra.springai.model.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.springai.model.provider.bedrock.BedrockChatModelFactory;
import com.aiplatform.infra.springai.model.provider.bedrock.BedrockEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.bedrock.BedrockSpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicChatModelFactory;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicSpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.provider.ollama.OllamaChatModelFactory;
import com.aiplatform.infra.springai.model.provider.ollama.OllamaEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.ollama.OllamaSpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.provider.openai.OpenAiCompatibleChatModelFactory;
import com.aiplatform.infra.springai.model.provider.openai.OpenAiCompatibleEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.openai.OpenAiCompatibleSpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.provider.vertexai.VertexAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.vertexai.VertexAiSpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;

class ProviderProbeClientRedactionTest {
    private static final String KNOWN_CREDENTIAL = "known-credential";
    private static final String ENCODED_CREDENTIAL = Base64.getEncoder().encodeToString(
            KNOWN_CREDENTIAL.getBytes(StandardCharsets.UTF_8)
    );
    private static final String ENCODED_JSON_SECRET = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"secretAccessKey\":\"json-secret\"}".getBytes(StandardCharsets.UTF_8)
    );
    private static final String FAILURE = "401 Authorization%253A%2520BeArEr%2520bearer-secret "
            + "url=https://provider.example/models?API%255FKEY%253Dquery%2520secret&region=us-east-1 "
            + "body=" + ENCODED_JSON_SECRET + " opaque=" + ENCODED_CREDENTIAL + " diagnostic=retained";

    @ParameterizedTest(name = "{0}")
    @MethodSource("representativeClients")
    void representativeProviderClientsUseTheSharedDeepRedactionSeam(
            String provider,
            String expectedCategory,
            SpringAiModelProbeClient client
    ) {
        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(
                request(provider),
                mock(ChatOptions.class)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message())
                .contains(expectedCategory, "diagnostic=retained", "API_KEY=***")
                .containsIgnoringCase("bearer ***")
                .doesNotContain(
                        "bearer-secret",
                        "query secret",
                        "json-secret",
                        KNOWN_CREDENTIAL,
                        ENCODED_CREDENTIAL,
                        ENCODED_JSON_SECRET
                );
    }

    private static Stream<Arguments> representativeClients() {
        return Stream.of(
                Arguments.of("openai", "鉴权失败", openAiClient()),
                Arguments.of("bedrock", "鉴权失败", bedrockClient()),
                Arguments.of("ollama", "模型运行时探测失败", ollamaClient()),
                Arguments.of("local", "本地模型运行时探测失败", localClient())
        );
    }

    private static SpringAiModelProbeClient openAiClient() {
        OpenAiCompatibleChatModelFactory chatFactory = mock(OpenAiCompatibleChatModelFactory.class);
        when(chatFactory.create(any(), any())).thenThrow(new RuntimeException(FAILURE));
        return new OpenAiCompatibleSpringAiModelProbeClient(
                chatFactory,
                mock(OpenAiCompatibleEmbeddingModelFactory.class)
        );
    }

    private static SpringAiModelProbeClient bedrockClient() {
        BedrockChatModelFactory chatFactory = mock(BedrockChatModelFactory.class);
        when(chatFactory.create(any(), any())).thenThrow(new RuntimeException(FAILURE));
        return new BedrockSpringAiModelProbeClient(chatFactory, mock(BedrockEmbeddingModelFactory.class));
    }

    @org.junit.jupiter.api.Test
    void vertexEmbeddingFailuresUseTheSharedDeepRedactionSeam() {
        VertexAiEmbeddingModelFactory embeddingFactory = mock(VertexAiEmbeddingModelFactory.class);
        when(embeddingFactory.create(any(), any())).thenThrow(new RuntimeException(FAILURE));
        VertexAiSpringAiModelProbeClient client = new VertexAiSpringAiModelProbeClient(embeddingFactory);
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "vertex",
                "vertex-ai",
                "https://provider.example",
                "gcp_service_account",
                "text-embedding-004",
                "EMBEDDING",
                8192,
                768,
                Map.of(),
                KNOWN_CREDENTIAL
        );

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(
                request,
                mock(EmbeddingOptions.class)
        ));

        assertThat(result.connected()).isFalse();
        assertThat(result.message())
                .contains("鉴权失败", "diagnostic=retained", "API_KEY=***")
                .containsIgnoringCase("bearer ***")
                .doesNotContain(
                        "bearer-secret",
                        "query secret",
                        "json-secret",
                        KNOWN_CREDENTIAL,
                        ENCODED_CREDENTIAL,
                        ENCODED_JSON_SECRET
                );
    }

    private static SpringAiModelProbeClient ollamaClient() {
        OllamaChatModelFactory chatFactory = mock(OllamaChatModelFactory.class);
        when(chatFactory.create(any(), any())).thenThrow(new RuntimeException(FAILURE));
        return new OllamaSpringAiModelProbeClient(chatFactory, mock(OllamaEmbeddingModelFactory.class));
    }

    private static SpringAiModelProbeClient localClient() {
        LocalDeterministicChatModelFactory chatFactory = mock(LocalDeterministicChatModelFactory.class);
        when(chatFactory.supports(any())).thenReturn(true);
        when(chatFactory.create(any(), any())).thenThrow(new RuntimeException(FAILURE));
        return new LocalDeterministicSpringAiModelProbeClient(
                mock(LocalDeterministicEmbeddingModelFactory.class),
                chatFactory
        );
    }

    private static SpringAiModelRuntimeRequest request(String provider) {
        return new SpringAiModelRuntimeRequest(
                provider,
                provider,
                "https://provider.example",
                "api_key",
                "test-model",
                "CHAT",
                8192,
                null,
                Map.of(),
                KNOWN_CREDENTIAL
        );
    }
}
