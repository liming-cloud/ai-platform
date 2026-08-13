package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/** 平台确定性供应商的模型发现器，仅处理平台专用请求。 */
public final class DeterministicPlatformProviderModelDiscoverer implements PlatformProviderModelDiscoverer {

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.DETERMINISTIC;
    }

    @Override
    public ProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
        if (!"memory".equals(request.endpoint().getScheme())) {
            return new ProviderModelDiscoveryObservation(
                    false, Set.of(), "DETERMINISTIC_ENDPOINT_REJECTED", Duration.ZERO, Instant.now());
        }
        return new ProviderModelDiscoveryObservation(
                true,
                Set.of(
                        new DiscoveredModel(
                                "deterministic-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT)),
                        new DiscoveredModel(
                                "deterministic-embedding", ModelType.EMBEDDING,
                                Set.of(ModelCapability.EMBEDDING))),
                "DETERMINISTIC_DISCOVERY_OK",
                Duration.ZERO,
                Instant.now());
    }
}
