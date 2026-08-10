package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** 确定性开发供应商的受控发现器，仅返回固定演示模型。 */
public final class DeterministicProviderModelDiscoverer implements ProviderModelDiscoverer {
    @Override
    public ProviderKind providerKind() {
        return ProviderKind.DETERMINISTIC;
    }

    @Override
    public ProviderModelDiscoveryObservation discover(ProviderModelDiscoveryRequest request) {
        if (!"memory".equals(request.endpoint().getScheme())) {
            return new ProviderModelDiscoveryObservation(
                    false,
                    Set.of(),
                    "DETERMINISTIC_ENDPOINT_REJECTED",
                    Duration.ZERO,
                    Instant.now());
        }
        return new ProviderModelDiscoveryObservation(
                true,
                Set.of(
                        new DiscoveredModel(
                                "deterministic-chat",
                                ModelType.CHAT,
                                Set.of(ModelCapability.CHAT)),
                        new DiscoveredModel(
                                "deterministic-embedding",
                                ModelType.EMBEDDING,
                                Set.of(ModelCapability.EMBEDDING))),
                "DETERMINISTIC_DISCOVERY_OK",
                Duration.ZERO,
                Instant.now());
    }
}
