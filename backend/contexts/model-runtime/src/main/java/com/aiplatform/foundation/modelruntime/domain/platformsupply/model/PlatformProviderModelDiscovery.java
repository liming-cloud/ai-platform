package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 平台连接当前修订的一次不可变模型发现事实，不带租户标识。 */
public final class PlatformProviderModelDiscovery implements AggregateRoot<PlatformProviderModelDiscoveryId> {

    private final PlatformProviderModelDiscoveryId id;
    private final PlatformProviderConnectionId connectionId;
    private final long connectionRevision;
    private final boolean succeeded;
    private final Set<DiscoveredModel> models;
    private final String resultCode;
    private final Duration elapsed;
    private final Instant discoveredAt;
    private final Instant expiresAt;
    private PlatformProviderModelDiscovery(
            PlatformProviderModelDiscoveryId id,
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            ProviderModelDiscoveryObservation observation,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "平台模型发现标识不能为空");
        this.connectionId = Objects.requireNonNull(connectionId, "平台连接标识不能为空");
        if (connectionRevision < 0) {
            throw new IllegalArgumentException("平台连接修订不能为负数");
        }
        this.connectionRevision = connectionRevision;
        observation = Objects.requireNonNull(observation, "平台模型发现观察值不能为空");
        this.succeeded = observation.succeeded();
        this.models = immutableModels(observation.models());
        this.resultCode = requireResultCode(observation.resultCode());
        this.elapsed = requireElapsed(observation.elapsed());
        this.discoveredAt = Objects.requireNonNull(observation.discoveredAt(), "平台模型发现时间不能为空");
        if (succeeded) {
            if (models.isEmpty() || expiresAt == null || !expiresAt.isAfter(discoveredAt)) {
                throw new IllegalArgumentException("成功平台模型发现必须有未过期候选");
            }
            this.expiresAt = expiresAt;
        } else if (!models.isEmpty() || expiresAt != null) {
            throw new IllegalArgumentException("失败平台模型发现不得携带候选或有效期");
        } else {
            this.expiresAt = null;
        }
    }

    /** 从受控发现观察值固化平台范围发现事实。 */
    public static PlatformProviderModelDiscovery capture(
            PlatformProviderModelDiscoveryId id,
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            ProviderModelDiscoveryObservation observation,
            Instant expiresAt) {
        observation = Objects.requireNonNull(observation, "平台模型发现观察值不能为空");
        return new PlatformProviderModelDiscovery(
                id,
                connectionId,
                connectionRevision,
                observation,
                observation.succeeded() ? expiresAt : null);
    }

    @Override
    public PlatformProviderModelDiscoveryId id() {
        return id;
    }

    public PlatformProviderConnectionId connectionId() {
        return connectionId;
    }

    public long connectionRevision() {
        return connectionRevision;
    }

    public boolean succeeded() {
        return succeeded;
    }

    public Set<DiscoveredModel> models() {
        return models;
    }

    public String resultCode() {
        return resultCode;
    }

    public Duration elapsed() {
        return elapsed;
    }

    public Instant discoveredAt() {
        return discoveredAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    private static Set<DiscoveredModel> immutableModels(Set<DiscoveredModel> values) {
        values = Objects.requireNonNull(values, "平台发现模型集合不能为空");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("平台发现模型集合不能包含空值");
        }
        return Set.copyOf(values);
    }

    private static String requireResultCode(String value) {
        value = Objects.requireNonNull(value, "平台模型发现结果编码不能为空");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 120) {
            throw new IllegalArgumentException("平台模型发现结果编码格式不正确");
        }
        return value;
    }

    private static Duration requireElapsed(Duration value) {
        value = Objects.requireNonNull(value, "平台模型发现耗时不能为空");
        if (value.isNegative()) {
            throw new IllegalArgumentException("平台模型发现耗时不能为负数");
        }
        return value;
    }
}
