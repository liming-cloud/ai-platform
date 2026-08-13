package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.time.Duration;
import java.util.Set;

/** 平台范围确定性探测不访问网络，也不解析租户或平台秘密。 */
public final class DeterministicPlatformProviderConnectionProbe
        implements PlatformProviderConnectionProbe {
    private static final Set<ModelCapability> CAPABILITIES =
            Set.of(ModelCapability.CHAT, ModelCapability.EMBEDDING);
    @Override public ProviderKind providerKind() { return ProviderKind.DETERMINISTIC; }
    @Override public ProviderProbeObservation probe(PlatformProviderProbeRequest request) {
        return "memory".equals(request.endpoint().getScheme())
                ? new ProviderProbeObservation(true, CAPABILITIES, Duration.ZERO, "DETERMINISTIC_OK")
                : new ProviderProbeObservation(false, Set.of(), Duration.ZERO, "DETERMINISTIC_ENDPOINT_REJECTED");
    }
}
