package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;

/** 平台供应链执行模型发现的受信任扩展端口。 */
public interface PlatformProviderModelDiscoverer {

    /** 返回该扩展可处理的固定供应商协议。 */
    ProviderKind providerKind();

    /** 返回该扩展唯一处理的权威发现协议。 */
    PlatformModelDiscoveryProfile discoveryProfile();

    /** 从已验证平台连接发现模型候选。 */
    PlatformProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request);
}
