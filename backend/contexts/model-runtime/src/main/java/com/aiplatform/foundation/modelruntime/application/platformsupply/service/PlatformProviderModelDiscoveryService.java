package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.DiscoverPlatformProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscoveryId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 编排平台范围模型发现；外部发现调用不处于数据库事务内。 */
public final class PlatformProviderModelDiscoveryService {

    private final PlatformProviderConnectionRepository connections;
    private final Map<DiscovererKey, PlatformProviderModelDiscoverer> discoverers;
    private final PlatformProviderModelDiscoveryStore store;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final Duration validity;

    public PlatformProviderModelDiscoveryService(
            PlatformProviderConnectionRepository connections,
            List<PlatformProviderModelDiscoverer> discoverers,
            PlatformProviderModelDiscoveryStore store,
            Clock clock,
            EntityIdGenerator identifiers,
            Duration validity) {
        this.connections = Objects.requireNonNull(connections, "平台供应商连接仓储不能为空");
        this.discoverers = index(discoverers);
        this.store = Objects.requireNonNull(store, "平台模型发现存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.validity = requirePositive(validity);
    }

    /**
     * 固化已验证平台连接当前修订的模型候选。
     *
     * @throws ModelCatalogPermissionDeniedException 当前会话不具有 `model-platform:write` 时抛出。
     */
    public DiscoverPlatformProviderModelsResult discover(
            DiscoverPlatformProviderModelsCommand command) {
        Objects.requireNonNull(command, "平台模型发现命令不能为空");
        requirePermission(command.context());
        var connection = connections.findById(
                        PlatformProviderConnectionId.parse(command.providerConnectionId()))
                .orElseThrow(() -> new IllegalArgumentException("平台供应商连接不存在"));
        if (connection.version() != command.expectedVersion()) {
            throw new IllegalStateException("平台供应商连接修订已变化");
        }
        if (connection.status() == ProviderConnectionStatus.DISABLED) {
            throw new IllegalStateException("已停用的平台供应商连接不能发现模型");
        }
        var discoveryProfile = connection.discoveryProfile();
        var discoverer = discoverers.get(new DiscovererKey(connection.providerKind(), discoveryProfile));
        var observation = discoverer == null
                ? unsupportedObservation()
                : discoveredAtNow(discoverer.discover(new PlatformProviderModelDiscoveryRequest(
                        connection.providerKind(), discoveryProfile, connection.endpoint(), connection.credentialReference(),
                        command.timeout())));
        var discoveredConnection = connection;
        if (connection.status() != ProviderConnectionStatus.VERIFIED) {
            discoveredConnection = observation.succeeded()
                    ? connection.recordSuccessfulProbe(observation.resultCode(), observation.discoveredAt())
                    : connection.recordFailedProbe(observation.resultCode(), observation.discoveredAt());
            if (!connections.update(discoveredConnection, command.expectedVersion())) {
                throw new IllegalStateException("平台供应商连接修订已变化");
            }
        }
        var discovery = PlatformProviderModelDiscovery.capture(
                new PlatformProviderModelDiscoveryId(identifiers.nextId()),
                discoveredConnection.id(),
                discoveredConnection.version(),
                observation,
                observation.discoveredAt().plus(validity));
        store.record(discovery);
        return new DiscoverPlatformProviderModelsResult(
                discovery.id().toString(),
                discovery.succeeded(),
                discovery.models(),
                discovery.resultCode(),
                discovery.elapsed(),
                discovery.discoveredAt(),
                discovery.expiresAt());
    }

    private PlatformProviderModelDiscoveryObservation unsupportedObservation() {
        return new PlatformProviderModelDiscoveryObservation(
                false, Set.of(), "PLATFORM_MODEL_DISCOVERY_UNSUPPORTED", Duration.ZERO, clock.instant());
    }

    private PlatformProviderModelDiscoveryObservation discoveredAtNow(
            PlatformProviderModelDiscoveryObservation observation) {
        observation = Objects.requireNonNull(observation, "平台模型发现扩展结果不能为空");
        return new PlatformProviderModelDiscoveryObservation(
                observation.succeeded(),
                observation.candidates(),
                observation.resultCode(),
                observation.elapsed(),
                clock.instant());
    }

    private static void requirePermission(PlatformRequestContext context) {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
    }

    private static Map<DiscovererKey, PlatformProviderModelDiscoverer> index(
            List<PlatformProviderModelDiscoverer> values) {
        var indexed = new HashMap<DiscovererKey, PlatformProviderModelDiscoverer>();
        for (var value : Objects.requireNonNull(values, "平台模型发现扩展不能为空")) {
            var key = new DiscovererKey(value.providerKind(), value.discoveryProfile());
            if (indexed.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("供应商协议与发现配置只能装配一个平台模型发现扩展");
            }
        }
        return Map.copyOf(indexed);
    }

    private record DiscovererKey(ProviderKind providerKind, PlatformModelDiscoveryProfile discoveryProfile) {
        private DiscovererKey {
            Objects.requireNonNull(providerKind, "供应商类型不能为空");
            Objects.requireNonNull(discoveryProfile, "模型发现协议不能为空");
        }
    }

    private static Duration requirePositive(Duration value) {
        value = Objects.requireNonNull(value, "模型发现有效期不能为空");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("模型发现有效期必须大于零");
        }
        return value;
    }
}
