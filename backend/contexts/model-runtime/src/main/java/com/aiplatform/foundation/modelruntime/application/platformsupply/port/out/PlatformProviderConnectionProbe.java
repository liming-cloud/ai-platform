package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;

/** 平台范围连接探测扩展，禁止接受租户身份或租户密钥解析器。 */
public interface PlatformProviderConnectionProbe {
    ProviderKind providerKind();
    ProviderProbeObservation probe(PlatformProviderProbeRequest request);
}
