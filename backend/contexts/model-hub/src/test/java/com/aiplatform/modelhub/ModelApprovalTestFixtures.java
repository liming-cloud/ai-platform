package com.aiplatform.modelhub;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigChange;
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

/** Production-shaped model approval fixtures shared by messaging and transaction tests. */
public final class ModelApprovalTestFixtures {
    private ModelApprovalTestFixtures() {
    }

    public static ModelConfigChange configChange(
            String tenantId,
            String requestId,
            String providerId,
            String modelConfigId,
            String operatorId,
            String traceId,
            Instant requestedAt
    ) {
        ModelProvider provider = activeProvider(tenantId, providerId, requestedAt);
        ModelConfig target = new ModelConfig(
                TenantId.of(tenantId), modelConfigId, providerId, "fixture-model", "Fixture model",
                ModelType.CHAT, 8_192, null, BigDecimal.ZERO, BigDecimal.ZERO,
                ModelConfigStatus.PENDING_APPROVAL, requestId,
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.NOT_TESTED, null, "", requestedAt, requestedAt
        );
        return ModelConfigChange.forModelConfigCreation(provider, target, operatorId, traceId, requestedAt);
    }

    public static ModelConfigChange providerChange(
            String tenantId,
            String requestId,
            String providerId,
            String providerName,
            String operatorId,
            String traceId,
            Instant requestedAt
    ) {
        ModelProvider provider = new ModelProvider(
                TenantId.of(tenantId), providerId, "openai-compatible", providerName,
                "https://api.example.com/v1", "api_key", null,
                ModelProviderStatus.PENDING_APPROVAL, requestId, true, requestedAt, requestedAt
        );
        return ModelConfigChange.forProviderCreation(provider, true, operatorId, traceId, requestedAt);
    }

    private static ModelProvider activeProvider(String tenantId, String providerId, Instant now) {
        return new ModelProvider(
                TenantId.of(tenantId), providerId, "openai-compatible", "Fixture provider",
                "https://api.example.com/v1", "api_key", new ModelSecretRef("secret-fixture", "****fixture"),
                ModelProviderStatus.ACTIVE, "REQ-PROVIDER-FIXTURE", true, now, now
        );
    }
}
