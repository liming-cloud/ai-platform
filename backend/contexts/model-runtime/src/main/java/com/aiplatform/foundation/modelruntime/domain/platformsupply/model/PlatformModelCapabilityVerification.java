package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 平台模型部署的一次不可变能力验证事实，不保存供应商原始响应。 */
public final class PlatformModelCapabilityVerification
        implements AggregateRoot<PlatformModelCapabilityVerificationId> {

    private final PlatformModelCapabilityVerificationId id;
    private final PlatformModelDeploymentId deploymentId;
    private final long deploymentRevision;
    private final PlatformProviderConnectionId providerConnectionId;
    private final long providerConnectionRevision;
    private final String modelCode;
    private final Set<ModelCapability> declaredCapabilities;
    private final Set<ModelCapability> observedCapabilities;
    private final boolean succeeded;
    private final String resultCode;
    private final Duration elapsed;
    private final Instant probedAt;
    private final Instant expiresAt;

    private PlatformModelCapabilityVerification(
            PlatformModelCapabilityVerificationId id,
            PlatformModelDeployment deployment,
            ModelCapabilityObservation observation,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "平台模型能力验证标识不能为空");
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
        observation = Objects.requireNonNull(observation, "模型探测结果不能为空");
        this.deploymentId = deployment.id();
        this.deploymentRevision = deployment.version();
        this.providerConnectionId = deployment.providerConnectionId();
        this.providerConnectionRevision = deployment.providerConnectionRevision();
        this.modelCode = deployment.modelCode();
        this.declaredCapabilities = deployment.capabilities();
        this.observedCapabilities = observation.capabilities();
        this.succeeded = observation.reachable()
                && observedCapabilities.containsAll(declaredCapabilities);
        this.resultCode = succeeded ? observation.detailCode()
                : observation.reachable() ? "MODEL_CAPABILITY_MISMATCH" : observation.detailCode();
        this.elapsed = observation.elapsed();
        this.probedAt = observation.probedAt();
        this.expiresAt = succeeded ? Objects.requireNonNull(expiresAt, "验证失效时间不能为空") : null;
    }

    /** 从受控探测观察值固化平台验证事实。 */
    public static PlatformModelCapabilityVerification capture(
            PlatformModelCapabilityVerificationId id,
            PlatformModelDeployment deployment,
            ModelCapabilityObservation observation,
            Instant expiresAt) {
        return new PlatformModelCapabilityVerification(id, deployment, observation, expiresAt);
    }

    @Override public PlatformModelCapabilityVerificationId id() { return id; }
    public PlatformModelDeploymentId deploymentId() { return deploymentId; }
    public long deploymentRevision() { return deploymentRevision; }
    public PlatformProviderConnectionId providerConnectionId() { return providerConnectionId; }
    public long providerConnectionRevision() { return providerConnectionRevision; }
    public String modelCode() { return modelCode; }
    public Set<ModelCapability> declaredCapabilities() { return declaredCapabilities; }
    public Set<ModelCapability> observedCapabilities() { return observedCapabilities; }
    public boolean succeeded() { return succeeded; }
    public String resultCode() { return resultCode; }
    public Duration elapsed() { return elapsed; }
    public Instant probedAt() { return probedAt; }
    public Instant expiresAt() { return expiresAt; }
}
