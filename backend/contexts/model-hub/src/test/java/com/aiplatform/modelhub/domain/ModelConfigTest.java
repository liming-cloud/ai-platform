package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelConfigTest {
    @Test
    void rejectsMissingOptionsInsteadOfEmptyRuntimeOptions() {
        assertThatThrownBy(() -> new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                null,
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("模型运行时 options 不能为空");
    }

    @Test
    void rejectsMissingPriceInsteadOfZeroFallback() {
        assertThatThrownBy(() -> new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                null,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(java.util.Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("输入单价不能为空");
    }

    @Test
    void rejectsTestedConnectivityWithoutCheckedAtOrMessage() {
        assertThatThrownBy(() -> new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(java.util.Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.FAILED,
                null,
                "模型供应商网络连接失败",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("连通性检查时间不能为空");

        assertThatThrownBy(() -> new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(java.util.Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.FAILED,
                Instant.parse("2026-05-25T02:01:00Z"),
                null,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性说明不能为空");
    }

    @Test
    void rejectsNotTestedConnectivityWithCheckedAtOrMessage() {
        assertThatThrownBy(() -> new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(java.util.Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.NOT_TESTED,
                Instant.parse("2026-05-25T02:01:00Z"),
                "",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未测试模型配置不得携带连通性检查时间");

        assertThatThrownBy(() -> new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(java.util.Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "模型供应商网络连接失败",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未测试模型配置不得携带连通性说明");
    }

    @Test
    void rejectsBlankRuntimeConnectivityMessage() {
        assertThatThrownBy(() -> new ModelRuntimeConnectivity(
                false,
                ModelConnectivityStatus.FAILED,
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性说明不能为空");
    }

    @Test
    void rejectsRuntimeConnectivityStateMismatch() {
        assertThatThrownBy(() -> new ModelRuntimeConnectivity(
                true,
                ModelConnectivityStatus.FAILED,
                "模型供应商网络连接失败"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性通过结果必须使用 PASSED 状态");

        assertThatThrownBy(() -> new ModelRuntimeConnectivity(
                false,
                ModelConnectivityStatus.PASSED,
                "模型供应商连接正常"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性失败结果必须使用 FAILED 状态");

        assertThatThrownBy(() -> new ModelRuntimeConnectivity(
                false,
                ModelConnectivityStatus.NOT_TESTED,
                "模型供应商尚未测试"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型连通性失败结果必须使用 FAILED 状态");
    }

    @Test
    void normalizesRequiredModelFactsBeforeRuntimeAndPolicyUse() {
        ModelConfig config = new ModelConfig(
                TenantId.of("2001"),
                " 4001 ",
                " 3001 ",
                " gpt-4.1-mini ",
                " GPT 4.1 Mini ",
                ModelType.CHAT,
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(java.util.Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T02:01:00Z"),
                " 模型供应商连接正常 ",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );

        assertThat(config.modelConfigId()).isEqualTo("4001");
        assertThat(config.providerId()).isEqualTo("3001");
        assertThat(config.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(config.modelName()).isEqualTo("GPT 4.1 Mini");
        assertThat(config.connectivityMessage()).isEqualTo("模型供应商连接正常");
    }
}
