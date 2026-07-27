package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderProbeRequest;
import java.time.Duration;
import java.util.Set;

/**
 * 不访问网络的确定性供应商，用于开发、契约测试和演示环境自检。
 *
 * <p>该实现不得用于伪装真实模型质量，只证明供应商扩展、能力描述和运行时装配链路。</p>
 */
public final class DeterministicProviderConnectionProbe implements ProviderConnectionProbe {

    private static final Set<ModelCapability> CAPABILITIES =
            Set.of(ModelCapability.CHAT, ModelCapability.EMBEDDING);

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.DETERMINISTIC;
    }

    @Override
    public ProviderProbeObservation probe(ProviderProbeRequest request) {
        if (!"memory".equals(request.endpoint().getScheme())) {
            return new ProviderProbeObservation(
                    false, Set.of(), Duration.ZERO, "DETERMINISTIC_ENDPOINT_REJECTED");
        }
        return new ProviderProbeObservation(
                true, CAPABILITIES, Duration.ZERO, "DETERMINISTIC_OK");
    }
}
