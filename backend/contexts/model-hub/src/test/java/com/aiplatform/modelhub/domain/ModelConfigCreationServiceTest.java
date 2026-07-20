package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConfigCreationServiceTest {
    private final ModelConfigCreationService service = new ModelConfigCreationService();

    @Test
    void createsPendingConfigWhenProviderConfigChangeRequiresApproval() {
        ModelConfig config = service.createModelConfig(
                TenantId.of("2001"),
                "4001",
                provider(true),
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "chat",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                Map.of("providerKind", "openai-compatible", "temperature", 0.2),
                "REQ-TEST",
                Instant.parse("2026-05-25T03:00:00Z")
        );

        assertThat(config.status()).isEqualTo(ModelConfigStatus.PENDING_APPROVAL);
        assertThat(config.modelType()).isEqualTo(ModelType.CHAT);
        assertThat(config.options().providerKind()).isEqualTo("openai-compatible");
        assertThat(config.options().values()).containsEntry("temperature", 0.2);
    }

    @Test
    void createsInactiveConfigUntilConnectivityPassesWhenProviderAllowsDirectConfigChange() {
        ModelConfig config = service.createModelConfig(
                TenantId.of("2001"),
                "4001",
                provider(false),
                "text-embedding-3-small",
                "Embedding Small",
                "EMBEDDING",
                8192,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of("providerKind", "openai-compatible", "dimensions", 1536),
                null,
                Instant.parse("2026-05-25T03:00:00Z")
        );

        assertThat(config.status()).isEqualTo(ModelConfigStatus.INACTIVE);
        assertThat(config.connectivityStatus()).isEqualTo(ModelConnectivityStatus.NOT_TESTED);
        assertThat(config.modelType()).isEqualTo(ModelType.EMBEDDING);
    }

    @Test
    void rejectsMissingContextWindowInsteadOfCreatingUnlimitedPolicy() {
        assertThatThrownBy(() -> service.createModelConfig(
                TenantId.of("2001"),
                "4001",
                provider(false),
                "text-embedding-3-small",
                "Embedding Small",
                "EMBEDDING",
                null,
                1536,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of("providerKind", "openai-compatible", "dimensions", 1536),
                null,
                Instant.parse("2026-05-25T03:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上下文窗口不能为空");
    }

    @Test
    void rejectsUnsupportedModelType() {
        assertThatThrownBy(() -> service.createModelConfig(
                TenantId.of("2001"),
                "4001",
                provider(false),
                "vision-model",
                "Vision Model",
                "VISION",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of("providerKind", "openai-compatible"),
                null,
                Instant.parse("2026-05-25T03:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型类型不支持");
    }

    @Test
    void rejectsMissingProviderKind() {
        assertThatThrownBy(() -> service.createModelConfig(
                TenantId.of("2001"),
                "4001",
                provider(false),
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of("temperature", 0.2),
                null,
                Instant.parse("2026-05-25T03:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerKind");
    }

    @Test
    void rejectsMissingOptionsInsteadOfEmptyRuntimeOptions() {
        assertThatThrownBy(() -> service.createModelConfig(
                TenantId.of("2001"),
                "4001",
                provider(false),
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                Instant.parse("2026-05-25T03:00:00Z")
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("模型运行时 options 不能为空");
    }

    private static ModelProvider provider(boolean approvalRequired) {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                approvalRequired,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }
}
