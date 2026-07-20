package com.aiplatform.infra.springai.model.provider.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;

class LocalDeterministicSpringAiModelOptionsStrategyTest {
    private final LocalDeterministicSpringAiModelOptionsStrategy strategy =
            new LocalDeterministicSpringAiModelOptionsStrategy();

    @Test
    void supportsOnlyCanonicalLocalChatAndEmbeddingRequests() {
        assertThat(strategy.supports(request("local-deterministic", "CHAT", null, Map.of()))).isTrue();
        assertThat(strategy.supports(request("local-deterministic", "EMBEDDING", 64, Map.of()))).isTrue();
        assertThat(strategy.supports(request("local-deterministic", "REASONING", null, Map.of()))).isFalse();
        assertThat(strategy.supports(request("ollama", "CHAT", null, Map.of()))).isFalse();
    }

    @Test
    void buildsFactoryCompatibleChatAndEmbeddingOptions() {
        ChatOptions chat = (ChatOptions) strategy.buildOptions(request(
                "local-deterministic",
                "CHAT",
                null,
                Map.of("temperature", 0.0, "maxTokens", 16)
        ));
        EmbeddingOptions embedding = (EmbeddingOptions) strategy.buildOptions(request(
                "local-deterministic",
                "EMBEDDING",
                64,
                Map.of()
        ));

        assertThat(chat.getModel()).isEqualTo("local-model");
        assertThat(chat.getTemperature()).isEqualTo(0.0);
        assertThat(chat.getMaxTokens()).isEqualTo(16);
        assertThat(embedding.getModel()).isEqualTo("local-model");
        assertThat(embedding.getDimensions()).isEqualTo(64);
    }

    @Test
    void rejectsOptionsOutsideTheModelSpecificAllowlist() {
        var chatValidation = strategy.validate(request(
                "local-deterministic",
                "CHAT",
                null,
                Map.of("dimensions", 64, "vendorMagic", true)
        ));
        var embeddingValidation = strategy.validate(request(
                "local-deterministic",
                "EMBEDDING",
                64,
                Map.of("temperature", 0.0)
        ));

        assertThat(chatValidation.valid()).isFalse();
        assertThat(chatValidation.summary())
                .contains("CHAT 不支持的本地模型 options 字段 dimensions")
                .contains("CHAT 不支持的本地模型 options 字段 vendorMagic");
        assertThat(embeddingValidation.valid()).isFalse();
        assertThat(embeddingValidation.summary())
                .contains("EMBEDDING 不支持的本地模型 options 字段 temperature");
    }

    @Test
    void rejectsExternalEndpointAndCredentialMaterialWithoutEchoingTheSecret() {
        String secret = "local-runtime-secret-must-not-leak";
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "https://external.example/v1",
                "api_key",
                "local-model",
                "CHAT",
                8192,
                null,
                Map.of(),
                secret
        );

        var validation = strategy.validate(request);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.summary())
                .contains("local://deterministic")
                .doesNotContain(secret);
    }

    @Test
    void requiresEmbeddingMetadataAndMatchingBoundedDimensions() {
        var missingMetadata = strategy.validate(request(
                "local-deterministic", "EMBEDDING", null, Map.of("dimensions", 64)
        ));
        var mismatch = strategy.validate(request(
                "local-deterministic", "EMBEDDING", 64, Map.of("dimensions", 32)
        ));
        var belowLowerBound = strategy.validate(request(
                "local-deterministic", "EMBEDDING", 0, Map.of("dimensions", 0)
        ));
        int upper = LocalDeterministicRuntimeContract.MAX_EMBEDDING_DIMENSION;
        var atUpperBound = strategy.validate(request(
                "local-deterministic", "EMBEDDING", upper, Map.of("dimensions", upper)
        ));
        var aboveUpperBound = strategy.validate(request(
                "local-deterministic", "EMBEDDING", upper + 1, Map.of("dimensions", upper + 1)
        ));

        assertThat(missingMetadata.valid()).isFalse();
        assertThat(missingMetadata.summary()).contains("embeddingDimension 不能为空");
        assertThat(mismatch.valid()).isFalse();
        assertThat(mismatch.summary()).contains("dimensions 必须与 embeddingDimension 一致");
        assertThat(belowLowerBound.valid()).isFalse();
        assertThat(belowLowerBound.summary()).contains("必须大于等于 1");
        assertThat(atUpperBound.valid()).isTrue();
        assertThat(aboveUpperBound.valid()).isFalse();
        assertThat(aboveUpperBound.summary()).contains("不得大于 " + upper);
    }

    private static SpringAiModelRuntimeRequest request(
            String providerKind,
            String modelType,
            Integer embeddingDimension,
            Map<String, Object> options
    ) {
        return new SpringAiModelRuntimeRequest(
                "local",
                providerKind,
                "local://deterministic",
                "no_auth",
                "local-model",
                modelType,
                8192,
                embeddingDimension,
                options,
                null
        );
    }
}
