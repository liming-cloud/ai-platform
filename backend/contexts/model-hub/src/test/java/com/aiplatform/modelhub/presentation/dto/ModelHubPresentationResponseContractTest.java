package com.aiplatform.modelhub.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelHubPresentationResponseContractTest {
    @Test
    void trimsProviderResponseFacts() {
        ModelProviderResponse response = new ModelProviderResponse(
                " 3001 ",
                " PENDING_APPROVAL ",
                " APR-20260531 "
        );

        assertThat(response.providerId()).isEqualTo("3001");
        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.approvalRequestId()).isEqualTo("APR-20260531");
    }

    @Test
    void trimsConfigResponseFactsAndConnectivityStatus() {
        ModelConfigResponse response = new ModelConfigResponse(
                " 3001 ",
                " 4001 ",
                " gpt-4.1-mini ",
                " CHAT ",
                " PENDING_APPROVAL ",
                " APR-20260531 ",
                " NOT_TESTED "
        );

        assertThat(response.providerId()).isEqualTo("3001");
        assertThat(response.modelConfigId()).isEqualTo("4001");
        assertThat(response.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(response.modelType()).isEqualTo("CHAT");
        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.approvalRequestId()).isEqualTo("APR-20260531");
        assertThat(response.connectivityStatus()).isEqualTo("NOT_TESTED");
    }

    @Test
    void trimsDetailResponseFacts() {
        ModelProviderDetailResponse response = new ModelProviderDetailResponse(
                " 3001 ",
                " openai ",
                " OpenAI ",
                " https://api.openai.example/v1 ",
                " PENDING_APPROVAL ",
                " APR-20260531 ",
                3
        );

        assertThat(response.providerId()).isEqualTo("3001");
        assertThat(response.providerCode()).isEqualTo("openai");
        assertThat(response.providerName()).isEqualTo("OpenAI");
        assertThat(response.endpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.approvalRequestId()).isEqualTo("APR-20260531");
    }

    @Test
    void normalizesNotTestedConfigDetailConnectivityMessage() {
        ModelConfigDetailResponse response = new ModelConfigDetailResponse(
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

        assertThat(response.connectivityStatus()).isEqualTo("NOT_TESTED");
        assertThat(response.connectivityMessage()).isEmpty();
    }

    @Test
    void trimsTestedConnectivityMessage() {
        ModelConnectivityTestResponse response = new ModelConnectivityTestResponse(
                "3001",
                "4001",
                "INACTIVE",
                false,
                " FAILED ",
                " 模型供应商网络连接失败 ",
                5
        );

        assertThat(response.connectivityStatus()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("模型供应商网络连接失败");
    }

    @Test
    void rejectsBlankRequiredFacts() {
        assertThatThrownBy(() -> new ModelProviderResponse(
                " ",
                "PENDING_APPROVAL",
                "APR-20260531"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商 ID不能为空");

        assertThatThrownBy(() -> new ModelProviderDetailResponse(
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
        assertThatThrownBy(() -> new ModelConfigDetailResponse(
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

        assertThatThrownBy(() -> new ModelConnectivityTestResponse(
                "3001",
                "4001",
                "INACTIVE",
                true,
                "FAILED",
                "模型供应商网络连接失败",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性通过结果必须使用 PASSED 状态");

        assertThatThrownBy(() -> new ModelConnectivityTestResponse(
                "3001",
                "4001",
                "INACTIVE",
                false,
                "PASSED",
                "模型供应商连接正常",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性失败结果必须使用 FAILED 状态");

        assertThatThrownBy(() -> new ModelConnectivityTestResponse(
                "3001",
                "4001",
                "INACTIVE",
                false,
                "FAILED",
                " ",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性说明不能为空");

        assertThatThrownBy(() -> new ModelConfigResponse(
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
