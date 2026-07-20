package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRuntimeValidationTest {
    @Test
    void normalizesModelRuntimeValidationMessagesBeforeApplicationSummary() {
        ModelRuntimeValidation validation = ModelRuntimeValidation.failed(List.of(
                " temperature 必须是数字 ",
                " maxTokens 必须是整数 "
        ));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.violations()).containsExactly("temperature 必须是数字", "maxTokens 必须是整数");
        assertThat(validation.summary()).isEqualTo("temperature 必须是数字；maxTokens 必须是整数");
    }

    @Test
    void rejectsPassedModelRuntimeValidationWithViolationMessages() {
        assertThatThrownBy(() -> new ModelRuntimeValidation(true, List.of("temperature 必须是数字")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通过结果不得携带模型配置校验失败明细");
    }

    @Test
    void rejectsFailedModelRuntimeValidationWithoutViolationMessages() {
        assertThatThrownBy(() -> ModelRuntimeValidation.failed(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("失败结果必须携带模型配置校验失败明细");
    }

    @Test
    void rejectsBlankModelRuntimeValidationMessages() {
        assertThatThrownBy(() -> ModelRuntimeValidation.failed(List.of("temperature 必须是数字", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型配置校验明细必须是非空字符串");
    }
}
