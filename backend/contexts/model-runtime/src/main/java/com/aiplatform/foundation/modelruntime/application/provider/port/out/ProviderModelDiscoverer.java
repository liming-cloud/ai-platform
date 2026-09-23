package com.aiplatform.foundation.modelruntime.application.provider.port.out;

import com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderModelDiscoveryObservation;

/** 厂商扩展执行模型枚举的受控端口。 */
public interface ProviderModelDiscoverer {

    /** 返回此受信任扩展能够处理的固定供应商协议。 */
    ProviderKind providerKind();

    /**
     * 从已验证连接执行受控模型发现。
     *
     * @throws IllegalArgumentException 请求不符合该协议最小发现合同时抛出。
     */
    ProviderModelDiscoveryObservation discover(ProviderModelDiscoveryRequest request);
}
