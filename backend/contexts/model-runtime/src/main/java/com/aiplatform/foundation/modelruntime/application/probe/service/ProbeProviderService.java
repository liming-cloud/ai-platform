package com.aiplatform.foundation.modelruntime.application.probe.service;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.extension.service.ExtensionContractMapper;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeResult;
import com.aiplatform.foundation.modelruntime.application.probe.exception.UnsupportedProviderException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认供应商探测编排服务。
 *
 * <p>扩展在启动时按协议类型建立唯一映射，重复实现会直接阻止启动，
 * 避免运行时因注入顺序产生不可预测的厂商选择。</p>
 */
public final class ProbeProviderService {

    private final Map<ProviderKind, ProviderConnectionProbe> probes;
    private final Clock clock;

    public ProbeProviderService(List<ProviderConnectionProbe> probes, Clock clock) {
        this.probes = indexProbes(probes);
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public ProviderProbeResult probe(ProviderProbeRequest request) {
        Objects.requireNonNull(request, "探测请求不能为空");
        var adapter = probes.get(request.providerKind());
        if (adapter == null) {
            throw new UnsupportedProviderException(request.providerKind());
        }
        var observation = adapter.probe(ExtensionContractMapper.probe(request));
        return new ProviderProbeResult(
                request.providerKind(),
                observation.reachable(),
                ExtensionContractMapper.domainCapabilities(observation.capabilities()),
                observation.elapsed(),
                observation.detailCode(),
                clock.instant());
    }

    private static Map<ProviderKind, ProviderConnectionProbe> indexProbes(
            List<ProviderConnectionProbe> candidates) {
        Objects.requireNonNull(candidates, "供应商探测扩展不能为空");
        var indexed = new EnumMap<ProviderKind, ProviderConnectionProbe>(ProviderKind.class);
        for (var candidate : candidates) {
            Objects.requireNonNull(candidate, "供应商探测扩展不能包含空值");
            var previous = indexed.putIfAbsent(ProviderKind.valueOf(candidate.providerKind().name()), candidate);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "供应商协议只能装配一个探测扩展: " + candidate.providerKind());
            }
        }
        return Map.copyOf(indexed);
    }
}
