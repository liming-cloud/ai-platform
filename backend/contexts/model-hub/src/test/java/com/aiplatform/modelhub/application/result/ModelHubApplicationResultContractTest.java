package com.aiplatform.modelhub.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelHubApplicationResultContractTest {
    @Test
    void trimsCreateProviderResultFacts() {
        CreateModelProviderResult result = new CreateModelProviderResult(
                " 3001 ",
                " PENDING_APPROVAL ",
                " APR-20260531 "
        );

        assertThat(result.providerId()).isEqualTo("3001");
        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.approvalRequestId()).isEqualTo("APR-20260531");
    }

    @Test
    void trimsCreateConfigResultFactsAndConnectivityStatus() {
        CreateModelConfigResult result = new CreateModelConfigResult(
                " 3001 ",
                " 4001 ",
                " gpt-4.1-mini ",
                " CHAT ",
                " PENDING_APPROVAL ",
                " APR-20260531 ",
                " NOT_TESTED "
        );

        assertThat(result.providerId()).isEqualTo("3001");
        assertThat(result.modelConfigId()).isEqualTo("4001");
        assertThat(result.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(result.modelType()).isEqualTo("CHAT");
        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.approvalRequestId()).isEqualTo("APR-20260531");
        assertThat(result.connectivityStatus()).isEqualTo("NOT_TESTED");
    }

    @Test
    void trimsDetailResultFacts() {
        ModelProviderDetailResult result = new ModelProviderDetailResult(
                " 3001 ",
                " openai ",
                " OpenAI ",
                " https://api.openai.example/v1 ",
                " PENDING_APPROVAL ",
                " APR-20260531 ",
                3
        );

        assertThat(result.providerId()).isEqualTo("3001");
        assertThat(result.providerCode()).isEqualTo("openai");
        assertThat(result.providerName()).isEqualTo("OpenAI");
        assertThat(result.endpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.approvalRequestId()).isEqualTo("APR-20260531");
    }

    @Test
    void normalizesNotTestedConfigDetailConnectivityMessage() {
        ModelConfigDetailResult result = new ModelConfigDetailResult(
                "3001",
                "4001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                "0.0004",
                "0.0016",
                "openai-compatible",
                "0.2",
                null,
                1024,
                "PENDING_APPROVAL",
                "APR-20260531",
                " NOT_TESTED ",
                null,
                4
        );

        assertThat(result.connectivityStatus()).isEqualTo("NOT_TESTED");
        assertThat(result.connectivityMessage()).isEmpty();
    }

    @Test
    void trimsTestedConnectivityMessage() {
        ModelConnectivityTestResult result = new ModelConnectivityTestResult(
                "3001",
                "4001",
                "INACTIVE",
                false,
                " FAILED ",
                " 模型供应商网络连接失败 ",
                5
        );

        assertThat(result.connectivityStatus()).isEqualTo("FAILED");
        assertThat(result.message()).isEqualTo("模型供应商网络连接失败");
    }

    @Test
    void rejectsBlankRequiredFacts() {
        assertThatThrownBy(() -> new CreateModelProviderResult(
                " ",
                "PENDING_APPROVAL",
                "APR-20260531"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商 ID不能为空");

        assertThatThrownBy(() -> new ModelProviderDetailResult(
                "3001",
                "openai",
                " ",
                "https://api.openai.example/v1",
                "PENDING_APPROVAL",
                null,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商名称不能为空");
    }

    @Test
    void rejectsConnectivityStateConflicts() {
        assertThatThrownBy(() -> new ModelConfigDetailResult(
                "3001",
                "4001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                "0.0004",
                "0.0016",
                "openai-compatible",
                null,
                null,
                null,
                "PENDING_APPROVAL",
                "APR-20260531",
                "NOT_TESTED",
                "模型供应商网络连接失败",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未测试模型配置不得携带连通性说明");

        assertThatThrownBy(() -> new ModelConnectivityTestResult(
                "3001",
                "4001",
                "INACTIVE",
                true,
                "FAILED",
                "模型供应商网络连接失败",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性通过结果必须使用 PASSED 状态");

        assertThatThrownBy(() -> new ModelConnectivityTestResult(
                "3001",
                "4001",
                "INACTIVE",
                false,
                "PASSED",
                "模型供应商连接正常",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性失败结果必须使用 FAILED 状态");

        assertThatThrownBy(() -> new ModelConnectivityTestResult(
                "3001",
                "4001",
                "INACTIVE",
                false,
                "FAILED",
                " ",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性说明不能为空");

        assertThatThrownBy(() -> new CreateModelConfigResult(
                "3001",
                "4001",
                "gpt-4.1-mini",
                "CHAT",
                "PENDING_APPROVAL",
                "APR-20260531",
                "UNKNOWN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性状态不合法");
    }
}
