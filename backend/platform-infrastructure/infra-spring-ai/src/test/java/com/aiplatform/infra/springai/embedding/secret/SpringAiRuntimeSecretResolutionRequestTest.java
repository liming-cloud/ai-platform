package com.aiplatform.infra.springai.embedding.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SpringAiRuntimeSecretResolutionRequestTest {
    @Test
    void retainsCompleteRuntimeAuthorityForCredentialResolution() {
        SpringAiRuntimeSecretResolutionRequest request = request("api_key", "9001");

        assertThat(request.tenantId()).isEqualTo("2001");
        assertThat(request.modelConfigId()).isEqualTo("4001");
        assertThat(request.providerId()).isEqualTo("3001");
        assertThat(request.providerCode()).isEqualTo("openai-main");
        assertThat(request.providerKind()).isEqualTo("openai-compatible");
        assertThat(request.endpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(request.authType()).isEqualTo("api_key");
        assertThat(request.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(request.modelConfigVersion()).isEqualTo(7);
        assertThat(request.modelType()).isEqualTo("CHAT");
        assertThat(request.effectiveOptions()).containsEntry("deploymentName", "enterprise-chat");
        assertThat(request.effectiveOptionsDigest()).isEqualTo("a".repeat(64));
        assertThat(request.secretRefId()).isEqualTo("9001");
        assertThat(request.requiresCredentialMaterial()).isTrue();
    }

    @Test
    void requiresSecretReferenceForCredentialAuthAndRejectsItForNoAuth() {
        assertThatThrownBy(() -> request("api_key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("凭证材料引用 ID不能为空");
        assertThatThrownBy(() -> request("no_auth", "9001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_auth")
                .hasMessageContaining("凭证材料引用");

        SpringAiRuntimeSecretResolutionRequest noAuth = request("no_auth", null);
        assertThat(noAuth.requiresCredentialMaterial()).isFalse();
        assertThat(noAuth.secretRefId()).isNull();
    }

    @Test
    void rejectsMalformedEffectiveOptionsDigestAndInvalidConfigVersion() {
        Map<String, Object> options = Map.of("deploymentName", "enterprise-chat");

        assertThatThrownBy(() -> new SpringAiRuntimeSecretResolutionRequest(
                "2001", "4001", "3001", "openai-main", "openai-compatible",
                "https://api.openai.example/v1", "api_key", "gpt-4.1-mini",
                7, "CHAT", options, "A".repeat(64), "9001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 位小写 SHA-256");
        assertThatThrownBy(() -> new SpringAiRuntimeSecretResolutionRequest(
                "2001", "4001", "3001", "openai-main", "openai-compatible",
                "https://api.openai.example/v1", "api_key", "gpt-4.1-mini",
                -1, "CHAT", options, "a".repeat(64), "9001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置版本");
    }

    private static SpringAiRuntimeSecretResolutionRequest request(String authType, String secretRefId) {
        Map<String, Object> options = Map.of("deploymentName", "enterprise-chat");
        return new SpringAiRuntimeSecretResolutionRequest(
                "2001",
                "4001",
                "3001",
                "openai-main",
                "openai-compatible",
                "https://api.openai.example/v1",
                authType,
                "gpt-4.1-mini",
                7,
                "chat",
                options,
                "a".repeat(64),
                secretRefId
        );
    }
}
