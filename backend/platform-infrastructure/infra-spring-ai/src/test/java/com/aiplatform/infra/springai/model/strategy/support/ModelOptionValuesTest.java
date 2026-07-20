package com.aiplatform.infra.springai.model.strategy.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelOptionValuesTest {
    @Test
    void returnsEmptyOnlyWhenOptionKeyIsAbsent() {
        assertThat(ModelOptionValues.doubleValue(Map.of(), "temperature")).isEmpty();
        assertThat(ModelOptionValues.integerValue(Map.of(), "maxTokens")).isEmpty();
        assertThat(ModelOptionValues.stringValue(Map.of(), "user")).isEmpty();
        assertThat(ModelOptionValues.booleanValue(Map.of(), "safePrompt")).isEmpty();
        assertThat(ModelOptionValues.stringListValue(Map.of(), "stopSequences")).isEmpty();
    }

    @Test
    void rejectsNonFiniteDoubleOptionValuesInsteadOfTreatingThemAsMissing() {
        assertThatThrownBy(() -> ModelOptionValues.doubleValue(Map.of("temperature", "NaN"), "temperature"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature 必须是数字");
    }

    @Test
    void rejectsFractionalIntegerOptionValuesInsteadOfTreatingThemAsMissing() {
        assertThatThrownBy(() -> ModelOptionValues.integerValue(Map.of("maxTokens", 1.5), "maxTokens"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTokens 必须是整数");
    }

    @Test
    void rejectsBlankStringOptionValuesInsteadOfTreatingThemAsMissing() {
        assertThatThrownBy(() -> ModelOptionValues.stringValue(Map.of("user", " "), "user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user 必须是非空字符串");
    }

    @Test
    void rejectsInvalidBooleanOptionValuesInsteadOfTreatingThemAsMissing() {
        assertThatThrownBy(() -> ModelOptionValues.booleanValue(Map.of("safePrompt", "yes"), "safePrompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safePrompt 必须是布尔值");
    }

    @Test
    void normalizesStringListOptionValuesBeforeRuntimeUse() {
        assertThat(ModelOptionValues.stringListValue(
                Map.of("stopSequences", List.of(" END ", "\nSTOP\t")),
                "stopSequences"
        )).contains(List.of("END", "STOP"));
    }

    @Test
    void rejectsBlankStringListOptionValuesInsteadOfPassingEmptyStopSequences() {
        assertThatThrownBy(() -> ModelOptionValues.stringListValue(
                Map.of("stopSequences", List.of("END", " ")),
                "stopSequences"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stopSequences 必须是字符串数组且元素不能为空");
    }

    @Test
    void rejectsNonStringListOptionValues() {
        assertThatThrownBy(() -> ModelOptionValues.stringListValue(
                Map.of("stopSequences", List.of("END", 1)),
                "stopSequences"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stopSequences 必须是字符串数组且元素不能为空");
    }
}
