package com.aiplatform.foundation.modelruntime.api.extension;

import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest;

/**
 * 供应商协议扩展必须实现的最小连接探测契约。
 *
 * <p>接口按能力拆分，避免扩展演变为同时承担聊天、向量、图像和语音的万能适配器。</p>
 */
public interface ProviderConnectionProbe {

    ProviderKind providerKind();

    ProviderProbeObservation probe(ProviderProbeRequest request);
}
