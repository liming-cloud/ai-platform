package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentProbe;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import java.time.Duration;

/** 平台确定性模型探测仅验证内存端点与声明能力，不访问网络或秘密。 */
public final class DeterministicPlatformModelDeploymentProbe implements PlatformModelDeploymentProbe {
    @Override public ProviderKind providerKind() { return ProviderKind.DETERMINISTIC; }
    @Override public ProviderProbeObservation probe(PlatformModelDeploymentProbeRequest request) {
        return "memory".equals(request.endpoint().getScheme())
                ? ProviderProbeObservation.of(true, request.requestedCapabilities(), Duration.ZERO, "DETERMINISTIC_MODEL_OK")
                : ProviderProbeObservation.of(false, java.util.Set.of(), Duration.ZERO, "DETERMINISTIC_ENDPOINT_REJECTED");
    }
}
