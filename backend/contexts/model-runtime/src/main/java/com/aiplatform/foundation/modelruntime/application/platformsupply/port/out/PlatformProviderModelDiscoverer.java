package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;

/** 平台供应链执行模型发现的受信任扩展端口。 */
public interface PlatformProviderModelDiscoverer {

    /** 返回该扩展可处理的固定供应商协议。 */
    ProviderKind providerKind();

    /** 从已验证平台连接发现模型候选。 */
    ProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request);
}
