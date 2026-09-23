package com.aiplatform.foundation.modelruntime.application.provider.service;

import com.aiplatform.foundation.modelruntime.application.provider.command.DiscoverProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.DiscoverProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.extension.service.ExtensionContractMapper;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 在可信租户边界编排厂商模型发现，不在事务内执行外部调用。 */
public final class ProviderModelDiscoveryService {

    private static final PermissionCode WRITE = new PermissionCode("model:write");

    private final ProviderConnectionRepository connections;
    private final Map<com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind,
            ProviderModelDiscoverer> discoverers;
    private final ProviderModelDiscoveryStore store;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final Duration validity;
    public ProviderModelDiscoveryService(
            ProviderConnectionRepository connections,
            List<ProviderModelDiscoverer> discoverers,
            ProviderModelDiscoveryStore store,
            Clock clock,
            EntityIdGenerator identifiers,
            Duration validity) {
        this.connections = Objects.requireNonNull(connections, "供应商连接仓储不能为空");
        this.store = Objects.requireNonNull(store, "模型发现存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.validity = requirePositive(validity);
        this.discoverers = index(discoverers);
    }

    /**
     * 发现已验证连接当前修订的受控候选模型并冻结结果。
     *
     * @throws ModelCatalogPermissionDeniedException 当前成员没有 `model:write` 时抛出。
     * @throws ProviderConnectionConcurrentModificationException 连接修订已改变时抛出。
     */
    public DiscoverProviderModelsResult discover(DiscoverProviderModelsCommand command) {
        Objects.requireNonNull(command, "发现命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new ModelCatalogPermissionDeniedException();
        }
        var connection = connections.findById(
                        command.context().tenantId(),
                        ProviderConnectionId.parse(command.providerConnectionId()))
                .orElseThrow(ProviderConnectionNotFoundException::new);
        if (connection.version() != command.expectedVersion()) {
            throw new ProviderConnectionConcurrentModificationException();
        }
        if (connection.status() == ProviderConnectionStatus.DISABLED) {
            throw new IllegalStateException("已停用的供应商连接不能发现模型");
        }
        var discoverer = discoverers.get(connection.providerKind());
        var observation = discoverer == null
                ? new ProviderModelDiscoveryObservation(
                        false, java.util.Set.of(), "MODEL_DISCOVERY_UNSUPPORTED", Duration.ZERO, clock.instant())
                : discoveredAtNow(ExtensionContractMapper.domainDiscovery(discoverer.discover(ExtensionContractMapper.discovery(new ProviderModelDiscoveryRequest(
                        command.context().tenantId(),
                        connection.providerKind(),
                        connection.endpoint(),
                        connection.credentialReference(),
                        command.timeout())))));
        var discoveredConnection = connection;
        if (connection.status() != ProviderConnectionStatus.VERIFIED) {
            discoveredConnection = observation.succeeded()
                    ? connection.recordSuccessfulProbe(observation.resultCode(), observation.discoveredAt())
                    : connection.recordFailedProbe(observation.resultCode(), observation.discoveredAt());
            if (!connections.update(discoveredConnection, command.expectedVersion())) {
                throw new ProviderConnectionConcurrentModificationException();
            }
        }
        var discovery = ProviderModelDiscovery.capture(
                new ProviderModelDiscoveryId(identifiers.nextId()),
                command.context().tenantId(),
                discoveredConnection.id(),
                discoveredConnection.version(),
                observation,
                observation.discoveredAt().plus(validity));
        store.record(discovery);
        return new DiscoverProviderModelsResult(
                discovery.id().toString(),
                discovery.succeeded(),
                discovery.models(),
                discovery.resultCode(),
                discovery.elapsed(),
                discovery.discoveredAt(),
                discovery.expiresAt());
    }

    private ProviderModelDiscoveryObservation discoveredAtNow(
            ProviderModelDiscoveryObservation observation) {
        observation = Objects.requireNonNull(observation, "模型发现扩展结果不能为空");
        return new ProviderModelDiscoveryObservation(
                observation.succeeded(),
                observation.models(),
                observation.resultCode(),
                observation.elapsed(),
                clock.instant());
    }

    private static Map<com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind,
            ProviderModelDiscoverer> index(List<ProviderModelDiscoverer> values) {
        var indexed = new EnumMap<com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind,
                ProviderModelDiscoverer>(
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.class);
        for (var value : Objects.requireNonNull(values, "模型发现扩展不能为空")) {
            if (indexed.putIfAbsent(ProviderKind.valueOf(value.providerKind().name()), value) != null) {
                throw new IllegalArgumentException("供应商协议只能装配一个发现扩展");
            }
        }
        return Map.copyOf(indexed);
    }

    private static Duration requirePositive(Duration value) {
        value = Objects.requireNonNull(value, "模型发现有效期不能为空");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("模型发现有效期必须大于零");
        }
        return value;
    }
}
