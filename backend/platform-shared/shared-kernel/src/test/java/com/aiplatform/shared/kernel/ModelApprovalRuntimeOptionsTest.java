package com.aiplatform.shared.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelApprovalRuntimeOptionsTest {
    @Test
    void canonicalizesStringsListsNumbersAndBedrockTruncateLikeRuntimeStrategies() {
        Map<String, Object> normalized = ModelApprovalRuntimeOptions.validateAndNormalize(Map.of(
                "providerKind", " bedrock ",
                "embeddingProvider", " cohere ",
                "inputType", " search_document ",
                "truncate", " END ",
                "stopSequences", List.of(" STOP ", " END "),
                "temperature", new BigDecimal("0.2000")
        ));

        assertThat(normalized)
                .containsEntry("providerKind", "bedrock")
                .containsEntry("embeddingProvider", "cohere")
                .containsEntry("inputType", "search_document")
                .containsEntry("truncate", "END")
                .containsEntry("temperature", new BigDecimal("0.2"));
        assertThat(normalized.get("stopSequences")).isEqualTo(List.of("STOP", "END"));
        assertThatThrownBy(() -> ModelApprovalRuntimeOptions.validateAndNormalize(Map.of(
                "providerKind", "bedrock",
                "truncate", true
        ))).hasMessageContaining("truncate").hasMessageContaining("字符串");
    }

    @Test
    void rejectsExtremeExponentBeforeAllocatingPlainText() {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThatThrownBy(() -> ModelApprovalRuntimeOptions.validateAndNormalize(Map.of(
                        "providerKind", "bedrock",
                        "temperature", new BigDecimal("1E+100000000")
                ))).hasMessageContaining("数字精度")
        );
    }

    @Test
    void bindsDecimalsToTheEffectiveDoubleAndRejectsFractionalIntegerOptions() {
        Map<String, Object> normalized = ModelApprovalRuntimeOptions.validateAndNormalize(Map.of(
                "providerKind", "openai-compatible",
                "temperature", new BigDecimal("0.12345678901234567890123456789"),
                "maxTokens", new BigDecimal("4096.000")
        ));

        assertThat(normalized)
                .containsEntry("temperature", BigDecimal.valueOf(0.12345678901234567890123456789d))
                .containsEntry("maxTokens", new BigDecimal("4096"));
        assertThatThrownBy(() -> ModelApprovalRuntimeOptions.validateAndNormalize(Map.of(
                "providerKind", "openai-compatible",
                "maxTokens", new BigDecimal("4096.5")
        ))).hasMessageContaining("maxTokens").hasMessageContaining("整数");
    }
}
