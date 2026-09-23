package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.application.extension.service.ExtensionContractMapper;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentProbe;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeResult;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 选择唯一平台模型探测扩展，未装配协议收敛为稳定失败结果。 */
public final class PlatformModelProbeProviderService {
    private final Map<ProviderKind, PlatformModelDeploymentProbe> probes;
    private final Clock clock;

    public PlatformModelProbeProviderService(List<PlatformModelDeploymentProbe> probes, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        var indexed = new EnumMap<ProviderKind, PlatformModelDeploymentProbe>(ProviderKind.class);
        for (var probe : Objects.requireNonNull(probes, "平台模型探测扩展不能为空")) {
            if (indexed.putIfAbsent(ProviderKind.valueOf(probe.providerKind().name()), probe) != null) {
                throw new IllegalArgumentException("供应商协议只能装配一个平台模型探测扩展");
            }
        }
        this.probes = Map.copyOf(indexed);
    }

    public ProviderProbeResult probe(PlatformModelDeploymentProbeRequest request) {
        request = Objects.requireNonNull(request, "平台模型探测请求不能为空");
        var probe = probes.get(request.providerKind());
        if (probe == null) {
            return new ProviderProbeResult(request.providerKind(), false, Set.of(), Duration.ZERO,
                    "PLATFORM_MODEL_PROBE_UNSUPPORTED", clock.instant());
        }
        var observation = Objects.requireNonNull(probe.probe(ExtensionContractMapper.platformModelProbe(request)), "平台模型探测结果不能为空");
        return new ProviderProbeResult(request.providerKind(), observation.reachable(),
                ExtensionContractMapper.domainCapabilities(observation.capabilities()),
                observation.elapsed(), observation.detailCode(), clock.instant());
    }
}
