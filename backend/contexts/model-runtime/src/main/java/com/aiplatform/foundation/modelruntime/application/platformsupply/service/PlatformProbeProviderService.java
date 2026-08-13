package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeResult;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 选择唯一的平台探测扩展，并把未装配协议缩减为稳定失败事实。 */
public final class PlatformProbeProviderService {
    private final Map<ProviderKind, PlatformProviderConnectionProbe> probes;
    private final Clock clock;
    public PlatformProbeProviderService(List<PlatformProviderConnectionProbe> probes, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        var indexed = new EnumMap<ProviderKind, PlatformProviderConnectionProbe>(ProviderKind.class);
        for (var probe : Objects.requireNonNull(probes, "平台探测扩展不能为空")) {
            if (indexed.putIfAbsent(probe.providerKind(), probe) != null) throw new IllegalArgumentException("供应商协议只能装配一个平台探测扩展");
        }
        this.probes = Map.copyOf(indexed);
    }
    public ProviderProbeResult probe(PlatformProviderProbeRequest request) {
        var probe = probes.get(request.providerKind());
        if (probe == null) return new ProviderProbeResult(request.providerKind(), false, Set.of(), Duration.ZERO, "PLATFORM_PROBE_UNSUPPORTED", clock.instant());
        var result = Objects.requireNonNull(probe.probe(request), "平台探测结果不能为空");
        return new ProviderProbeResult(request.providerKind(), result.reachable(), result.capabilities(), result.elapsed(), result.detailCode(), clock.instant());
    }
}
