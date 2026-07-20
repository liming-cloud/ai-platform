package com.aiplatform.infra.springai.model.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAiModelValidationResultTest {
    @Test
    void normalizesValidationViolationMessagesBeforeGatewaySummary() {
        SpringAiModelValidationResult result = SpringAiModelValidationResult.failed(List.of(
                " temperature 必须是数字 ",
                " maxTokens 必须是整数 "
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).containsExactly("temperature 必须是数字", "maxTokens 必须是整数");
        assertThat(result.summary()).isEqualTo("temperature 必须是数字；maxTokens 必须是整数");
    }

    @Test
    void rejectsPassedResultWithViolationMessages() {
        assertThatThrownBy(() -> new SpringAiModelValidationResult(true, List.of("temperature 必须是数字")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通过结果不得携带校验失败明细");
    }

    @Test
    void rejectsFailedResultWithoutViolationMessages() {
        assertThatThrownBy(() -> SpringAiModelValidationResult.failed(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("失败结果必须携带校验失败明细");
    }

    @Test
    void rejectsBlankValidationViolationMessages() {
        assertThatThrownBy(() -> SpringAiModelValidationResult.failed(List.of("temperature 必须是数字", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("校验失败明细必须是非空字符串");
    }
}
