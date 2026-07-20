package com.aiplatform.modelhub.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConfigActivationPolicyTest {
    private final ModelConfigActivationPolicy policy = new ModelConfigActivationPolicy();

    @Test
    void inactivatesActiveConfigWhenConnectivityFails() {
        Instant updatedAt = Instant.parse("2026-05-25T09:00:00Z");

        ModelConfig result = policy.activateWhenReady(
                provider(ModelProviderStatus.ACTIVE),
                config(ModelConfigStatus.ACTIVE, ModelConnectivityStatus.FAILED),
                updatedAt
        );

        assertThat(result.status()).isEqualTo(ModelConfigStatus.INACTIVE);
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
        assertThat(result.connectivityStatus()).isEqualTo(ModelConnectivityStatus.FAILED);
    }

    @Test
    void activatesInactiveConfigOnlyAfterConnectivityPasses() {
        Instant updatedAt = Instant.parse("2026-05-25T09:00:00Z");

        ModelConfig result = policy.activateWhenReady(
                provider(ModelProviderStatus.ACTIVE),
                config(ModelConfigStatus.INACTIVE, ModelConnectivityStatus.PASSED),
                updatedAt
        );

        assertThat(result.status()).isEqualTo(ModelConfigStatus.ACTIVE);
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void keepsPendingConfigUnchangedEvenWhenConnectivityWasRecordedAsPassed() {
        ModelConfig config = config(ModelConfigStatus.PENDING_APPROVAL, ModelConnectivityStatus.PASSED);

        ModelConfig result = policy.activateWhenReady(
                provider(ModelProviderStatus.ACTIVE),
                config,
                Instant.parse("2026-05-25T09:00:00Z")
        );

        assertThat(result).isSameAs(config);
    }

    @Test
    void keepsRejectedConfigUnchangedEvenWhenConnectivityWasRecordedAsPassed() {
        ModelConfig config = config(ModelConfigStatus.REJECTED, ModelConnectivityStatus.PASSED);

        ModelConfig result = policy.activateWhenReady(
                provider(ModelProviderStatus.ACTIVE),
                config,
                Instant.parse("2026-05-25T09:00:00Z")
        );

        assertThat(result).isSameAs(config);
    }

    @Test
    void keepsDeprecatedConfigUnchangedWhenConnectivityChanges() {
        ModelConfig config = config(ModelConfigStatus.DEPRECATED, ModelConnectivityStatus.PASSED);

        ModelConfig result = policy.activateWhenReady(
                provider(ModelProviderStatus.ACTIVE),
                config,
                Instant.parse("2026-05-25T09:00:00Z")
        );

        assertThat(result).isSameAs(config);
    }

    @Test
    void providerRejectionPreservesPendingModelApprovalFact() {
        ModelConfig config = config(ModelConfigStatus.PENDING_APPROVAL, ModelConnectivityStatus.NOT_TESTED);

        ModelConfig result = policy.inactivateWhenProviderRejected(
                config,
                Instant.parse("2026-05-25T09:00:00Z")
        );

        assertThat(result).isSameAs(config);
    }

    private static ModelProvider provider(ModelProviderStatus status) {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                status,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelConfig config(ModelConfigStatus status, ModelConnectivityStatus connectivityStatus) {
        Instant checkedAt = connectivityStatus == ModelConnectivityStatus.NOT_TESTED
                ? null
                : Instant.parse("2026-05-25T08:00:00Z");
        String message = connectivityStatus == ModelConnectivityStatus.NOT_TESTED
                ? ""
                : "连接成功";
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                status,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible", "temperature", 0.2)),
                connectivityStatus,
                checkedAt,
                message,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T08:00:00Z")
        );
    }
}
