package com.aiplatform.foundation.modelruntime.application.probe.port.out;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderProbeRequest;

/**
 * 供应商协议扩展必须实现的最小连接探测契约。
 *
 * <p>接口按能力拆分，避免扩展演变为同时承担聊天、向量、图像和语音的万能适配器。</p>
 */
public interface ProviderConnectionProbe {

    ProviderKind providerKind();

    ProviderProbeObservation probe(ProviderProbeRequest request);
}
