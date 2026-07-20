package com.aiplatform.appstudio.domain.port.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppModelRuntimeDescriptorTest {
    @Test
    void normalizesRuntimeDescriptorFacts() {
        AppModelRuntimeDescriptor descriptor = new AppModelRuntimeDescriptor(
                " 9001 ",
                " 3001 ",
                " openai ",
                " openai-compatible ",
                new AppModelRuntimeConnectionRef(" https://api.openai.example/v1 ", " api_key ", " secret-1 "),
                " gpt-4.1-mini ",
                7,
                " chat ",
                Set.of(" chat ", "tool-calling"),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                " policy-v1 "
        );

        assertThat(descriptor.modelConfigId()).isEqualTo("9001");
        assertThat(descriptor.providerId()).isEqualTo("3001");
        assertThat(descriptor.providerCode()).isEqualTo("openai");
        assertThat(descriptor.providerKind()).isEqualTo("openai-compatible");
        assertThat(descriptor.connectionRef().endpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(descriptor.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(descriptor.capabilities()).containsExactlyInAnyOrder("chat", "tool-calling");
        assertThat(descriptor.policyVersion()).isEqualTo("policy-v1");
    }

    @Test
    void rejectsBlankRuntimeDescriptorFacts() {
        assertThatThrownBy(() -> new AppModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new AppModelRuntimeConnectionRef("https://api.openai.example/v1", "api_key", "secret-1"),
                "gpt-4.1-mini",
                7,
                "CHAT",
                Set.of(" "),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                "policy-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型能力不能为空");
    }
}
