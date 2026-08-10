package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 指定模型在冻结连接修订上的一次不可变能力验证事实。
 *
 * <p>验证事实只保留稳定的能力、分类和时间摘要，绝不保存 Endpoint、凭据、供应商原始响应或模型输入输出。</p>
 */
public final class ModelCapabilityVerification
        implements AggregateRoot<ModelCapabilityVerificationId> {

    private final ModelCapabilityVerificationId id;
    private final TenantId tenantId;
    private final ModelDeploymentId deploymentId;
    private final long deploymentVersion;
    private final ProviderConnectionId providerConnectionId;
    private final long providerConnectionRevision;
    private final String modelCode;
    private final Set<ModelCapability> declaredCapabilities;
    private final Set<ModelCapability> observedCapabilities;
    private final boolean succeeded;
    private final String resultCode;
    private final Duration elapsed;
    private final Instant probedAt;
    private final Instant expiresAt;

    private ModelCapabilityVerification(
            ModelCapabilityVerificationId id,
            TenantId tenantId,
            ModelDeploymentId deploymentId,
            long deploymentVersion,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            Set<ModelCapability> declaredCapabilities,
            Set<ModelCapability> observedCapabilities,
            boolean succeeded,
            String resultCode,
            Duration elapsed,
            Instant probedAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "模型能力验证标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.deploymentId = Objects.requireNonNull(deploymentId, "模型部署标识不能为空");
        if (deploymentVersion < 0) {
            throw new IllegalArgumentException("模型部署修订不能为负数");
        }
        this.deploymentVersion = deploymentVersion;
        this.providerConnectionId = Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (providerConnectionRevision < 0) {
            throw new IllegalArgumentException("供应商连接修订不能为负数");
        }
        this.providerConnectionRevision = providerConnectionRevision;
        this.modelCode = requireText(modelCode, "模型代码");
        this.declaredCapabilities = immutableCapabilities(declaredCapabilities, "声明能力");
        this.observedCapabilities = immutableCapabilities(observedCapabilities, "实测能力");
        this.succeeded = succeeded;
        this.resultCode = requireText(resultCode, "验证结果编码");
        this.elapsed = requireElapsed(elapsed);
        this.probedAt = Objects.requireNonNull(probedAt, "探测时间不能为空");
        if (succeeded) {
            this.expiresAt = Objects.requireNonNull(expiresAt, "成功验证失效时间不能为空");
            if (!expiresAt.isAfter(probedAt)) {
                throw new IllegalArgumentException("成功验证失效时间必须晚于探测时间");
            }
        } else if (expiresAt != null) {
            throw new IllegalArgumentException("失败验证不得携带失效时间");
        } else {
            this.expiresAt = null;
        }
    }

    /**
     * 从受控探测观察值固化一次验证事实。
     */
    public static ModelCapabilityVerification capture(
            ModelCapabilityVerificationId id,
            ModelDeployment deployment,
            ModelCapabilityObservation probe,
            Instant expiresAt) {
        id = Objects.requireNonNull(id, "模型能力验证标识不能为空");
        deployment = Objects.requireNonNull(deployment, "模型部署不能为空");
        probe = Objects.requireNonNull(probe, "模型探测结果不能为空");
        var capabilitiesMatch = probe.reachable()
                && probe.capabilities().containsAll(deployment.capabilities());
        var resultCode = capabilitiesMatch
                ? probe.detailCode()
                : probe.reachable() ? "MODEL_CAPABILITY_MISMATCH" : probe.detailCode();
        return new ModelCapabilityVerification(
                id,
                deployment.tenantId(),
                deployment.id(),
                deployment.version(),
                deployment.providerConnectionId(),
                deployment.providerConnectionRevision(),
                deployment.modelCode(),
                deployment.capabilities(),
                probe.capabilities(),
                capabilitiesMatch,
                resultCode,
                probe.elapsed(),
                probe.probedAt(),
                capabilitiesMatch ? expiresAt : null);
    }

    @Override
    public ModelCapabilityVerificationId id() {
        return id;
    }

    public TenantId tenantId() { return tenantId; }
    public ModelDeploymentId deploymentId() { return deploymentId; }
    public long deploymentVersion() { return deploymentVersion; }
    public ProviderConnectionId providerConnectionId() { return providerConnectionId; }
    public long providerConnectionRevision() { return providerConnectionRevision; }
    public String modelCode() { return modelCode; }
    public Set<ModelCapability> declaredCapabilities() { return declaredCapabilities; }
    public Set<ModelCapability> observedCapabilities() { return observedCapabilities; }
    public boolean succeeded() { return succeeded; }
    public String resultCode() { return resultCode; }
    public Duration elapsed() { return elapsed; }
    public Instant probedAt() { return probedAt; }
    public Instant expiresAt() { return expiresAt; }

    private static Set<ModelCapability> immutableCapabilities(
            Set<ModelCapability> values,
            String field) {
        values = Objects.requireNonNull(values, field + "不能为空");
        for (var value : values) {
            if (value == null) {
                throw new IllegalArgumentException(field + "不能包含空值");
            }
        }
        return Set.copyOf(values);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + "不能为空");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 120) {
            throw new IllegalArgumentException(field + "格式不正确");
        }
        return value;
    }

    private static Duration requireElapsed(Duration value) {
        value = Objects.requireNonNull(value, "探测耗时不能为空");
        if (value.isNegative()) {
            throw new IllegalArgumentException("探测耗时不能为负数");
        }
        return value;
    }
}
