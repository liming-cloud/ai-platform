package com.aiplatform.foundation.modelruntime.api.probe;

import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeResult;

/**
 * 验证供应商配置是否可进入发布流程的应用用例。
 */
public interface ProbeProviderUseCase {

    ProviderProbeResult probe(ProviderProbeRequest request);
}
