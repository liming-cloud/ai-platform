package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelRuntimeOptionsTest {
    @Test
    void requiresProviderKindAtConstruction() {
        assertThatThrownBy(() -> new ModelRuntimeOptions(Map.of("temperature", 0.2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerKind");
    }

    @Test
    void rejectsNonStringProviderKindInsteadOfStringValueOfFallback() {
        assertThatThrownBy(() -> new ModelRuntimeOptions(Map.of("providerKind", 123)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是字符串");
    }

    @Test
    void normalizesProviderKindBeforeRuntimeUseAndPolicyPublishing() {
        ModelRuntimeOptions options = new ModelRuntimeOptions(Map.of(
                "providerKind", " openai-compatible ",
                "temperature", 0.2
        ));

        assertThat(options.providerKind()).isEqualTo("openai-compatible");
        assertThat(options.values()).containsEntry("providerKind", "openai-compatible");
        assertThat(options.springAiOptions()).doesNotContainKey("providerKind");
    }
}
