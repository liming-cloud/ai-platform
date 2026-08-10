package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 指定厂商连接修订的一次不可变模型发现事实。
 */
public final class ProviderModelDiscovery implements AggregateRoot<ProviderModelDiscoveryId> {

    private final ProviderModelDiscoveryId id;
    private final TenantId tenantId;
    private final ProviderConnectionId providerConnectionId;
    private final long providerConnectionRevision;
    private final boolean succeeded;
    private final Set<DiscoveredModel> models;
    private final String resultCode;
    private final Duration elapsed;
    private final Instant discoveredAt;
    private final Instant expiresAt;

    private ProviderModelDiscovery(
            ProviderModelDiscoveryId id,
            TenantId tenantId,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            boolean succeeded,
            Set<DiscoveredModel> models,
            String resultCode,
            Duration elapsed,
            Instant discoveredAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "模型发现标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.providerConnectionId = Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (providerConnectionRevision < 0) {
            throw new IllegalArgumentException("供应商连接修订不能为负数");
        }
        this.providerConnectionRevision = providerConnectionRevision;
        this.succeeded = succeeded;
        this.models = immutableModels(models);
        this.resultCode = requireResultCode(resultCode);
        this.elapsed = requireElapsed(elapsed);
        this.discoveredAt = Objects.requireNonNull(discoveredAt, "模型发现时间不能为空");
        if (succeeded) {
            if (this.models.isEmpty()) {
                throw new IllegalArgumentException("成功模型发现必须至少包含一个候选模型");
            }
            this.expiresAt = Objects.requireNonNull(expiresAt, "成功模型发现失效时间不能为空");
            if (!expiresAt.isAfter(discoveredAt)) {
                throw new IllegalArgumentException("成功模型发现失效时间必须晚于发现时间");
            }
        } else if (!this.models.isEmpty() || expiresAt != null) {
            throw new IllegalArgumentException("失败模型发现不得携带候选模型或失效时间");
        } else {
            this.expiresAt = null;
        }
    }

    /**
     * 从受控发现观察值固化一次发现事实。
     */
    public static ProviderModelDiscovery capture(
            ProviderModelDiscoveryId id,
            TenantId tenantId,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            ProviderModelDiscoveryObservation observation,
            Instant expiresAt) {
        observation = Objects.requireNonNull(observation, "模型发现观察值不能为空");
        return new ProviderModelDiscovery(
                id,
                tenantId,
                providerConnectionId,
                providerConnectionRevision,
                observation.succeeded(),
                observation.models(),
                observation.resultCode(),
                observation.elapsed(),
                observation.discoveredAt(),
                observation.succeeded() ? expiresAt : null);
    }

    @Override
    public ProviderModelDiscoveryId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ProviderConnectionId providerConnectionId() { return providerConnectionId; }
    public long providerConnectionRevision() { return providerConnectionRevision; }
    public boolean succeeded() { return succeeded; }
    public Set<DiscoveredModel> models() { return models; }
    public String resultCode() { return resultCode; }
    public Duration elapsed() { return elapsed; }
    public Instant discoveredAt() { return discoveredAt; }
    public Instant expiresAt() { return expiresAt; }

    /**
     * 判断成功发现事实是否包含指定模型类型的候选。
     */
    public boolean contains(String modelCode, ModelType modelType) {
        return succeeded && models.stream().anyMatch(model ->
                model.modelCode().equals(modelCode) && model.modelType() == modelType);
    }

    private static Set<DiscoveredModel> immutableModels(Set<DiscoveredModel> values) {
        values = Objects.requireNonNull(values, "发现模型集合不能为空");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("发现模型集合不能包含空值");
        }
        return Set.copyOf(values);
    }

    private static String requireResultCode(String value) {
        Objects.requireNonNull(value, "模型发现结果编码不能为空");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 120) {
            throw new IllegalArgumentException("模型发现结果编码格式不正确");
        }
        return value;
    }

    private static Duration requireElapsed(Duration value) {
        value = Objects.requireNonNull(value, "模型发现耗时不能为空");
        if (value.isNegative()) {
            throw new IllegalArgumentException("模型发现耗时不能为负数");
        }
        return value;
    }
}
