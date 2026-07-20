package com.aiplatform.infra.springai.model.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpringAiModelConnectivityResultTest {
    @Test
    void normalizesConnectivityStatusAndMessageBeforeModelHubMapping() {
        SpringAiModelConnectivityResult result = new SpringAiModelConnectivityResult(
                true,
                " PASSED ",
                " 模型运行时探测通过 "
        );

        assertThat(result.connected()).isTrue();
        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.message()).isEqualTo("模型运行时探测通过");
    }

    @Test
    void rejectsBlankConnectivityStatusAndMessage() {
        assertThatThrownBy(() -> new SpringAiModelConnectivityResult(true, " ", "模型运行时探测通过"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("连通性状态不能为空");
        assertThatThrownBy(() -> new SpringAiModelConnectivityResult(true, "PASSED", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("连通性说明不能为空");
    }

    @Test
    void rejectsConnectivityStateMismatch() {
        assertThatThrownBy(() -> new SpringAiModelConnectivityResult(
                true,
                "FAILED",
                "模型运行时探测失败"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("连通性通过结果必须使用 PASSED 状态");

        assertThatThrownBy(() -> new SpringAiModelConnectivityResult(
                false,
                "PASSED",
                "模型运行时探测通过"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("连通性失败结果必须使用 FAILED 状态");
    }
}
