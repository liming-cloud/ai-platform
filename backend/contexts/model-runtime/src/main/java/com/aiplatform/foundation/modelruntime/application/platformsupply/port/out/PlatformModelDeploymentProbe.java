package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;

/** 平台模型能力探测扩展，禁止租户身份和租户密钥解析器进入该边界。 */
public interface PlatformModelDeploymentProbe {
    ProviderKind providerKind();
    ProviderProbeObservation probe(PlatformModelDeploymentProbeRequest request);
}
