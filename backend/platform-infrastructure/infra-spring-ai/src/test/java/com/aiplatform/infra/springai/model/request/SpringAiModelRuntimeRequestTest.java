package com.aiplatform.infra.springai.model.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringAiModelRuntimeRequestTest {
    @Test
    void normalizesRuntimeRoutingFactsBeforeProviderSelection() {
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                " openai-local ",
                " openai-compatible ",
                " https://models.example.com/v1 ",
                " api_key ",
                " gpt-4.1-mini ",
                " CHAT ",
                128000,
                null,
                Map.of("temperature", 0.2),
                " sk-test "
        );

        assertThat(request.providerCode()).isEqualTo("openai-local");
        assertThat(request.providerKind()).isEqualTo("openai-compatible");
        assertThat(request.endpoint()).isEqualTo("https://models.example.com/v1");
        assertThat(request.authType()).isEqualTo("api_key");
        assertThat(request.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(request.modelType()).isEqualTo("CHAT");
    }

    @Test
    void rejectsMissingOptionsInsteadOfEmptyRuntimeOptions() {
        assertThatThrownBy(() -> new SpringAiModelRuntimeRequest(
                "openai-local",
                "openai-compatible",
                "https://models.example.com/v1",
                "api_key",
                "gpt-4.1-mini",
                "CHAT",
                128000,
                null,
                null,
                "sk-test"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时 options不能为空");
    }

    @Test
    void rejectsDamagedOptionsInsteadOfJdkNullPointerException() {
        Map<String, Object> optionsWithNullValue = new LinkedHashMap<>();
        optionsWithNullValue.put("temperature", null);
        Map<String, Object> optionsWithNullKey = new LinkedHashMap<>();
        optionsWithNullKey.put(null, 0.2);

        assertThatThrownBy(() -> request(optionsWithNullValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时 options value不能为空");
        assertThatThrownBy(() -> request(optionsWithNullKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时 options key不能为空");
    }

    private static SpringAiModelRuntimeRequest request(Map<String, Object> options) {
        return new SpringAiModelRuntimeRequest(
                "openai-local",
                "openai-compatible",
                "https://models.example.com/v1",
                "api_key",
                "gpt-4.1-mini",
                "CHAT",
                128000,
                null,
                options,
                "sk-test"
        );
    }
}
