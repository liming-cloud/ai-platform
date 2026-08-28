package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
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
    public PlatformModelDiscoveryProfile discoveryProfile() {
        return PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG;
    }

    @Override
    public PlatformProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
        if (!"memory".equals(request.endpoint().getScheme())) {
            return new PlatformProviderModelDiscoveryObservation(
                    false, Set.of(), "DETERMINISTIC_ENDPOINT_REJECTED", Duration.ZERO, Instant.now());
        }
        return new PlatformProviderModelDiscoveryObservation(
                true,
                Set.of(
                        PlatformDiscoveredModelCandidate.confirmedByProvider(
                                "deterministic-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT)),
                        PlatformDiscoveredModelCandidate.confirmedByProvider(
                                "deterministic-embedding", ModelType.EMBEDDING,
                                Set.of(ModelCapability.EMBEDDING))),
                "DETERMINISTIC_DISCOVERY_OK",
                Duration.ZERO,
                Instant.now());
    }
}
