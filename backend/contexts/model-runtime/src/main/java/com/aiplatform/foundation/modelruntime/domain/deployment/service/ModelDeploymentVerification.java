package com.aiplatform.foundation.modelruntime.domain.deployment.service;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.operation.ModelDeploymentVerificationResult;
import java.time.Instant;
import java.util.Objects;

/**
 * 收回模型能力观察、验证事实和部署状态转换一致性的无状态领域服务。
 */
public final class ModelDeploymentVerification {

    private ModelDeploymentVerification() {
    }

    /**
     * 固化本次探测并仅在能力已实测覆盖声明时验证草稿部署。
     *
     * @throws IllegalStateException 当部署不是草稿时拒绝。
     */
    public static ModelDeploymentVerificationResult verify(
            ModelDeployment deployment,
            ModelCapabilityVerificationId verificationId,
            ModelCapabilityObservation probe,
            Instant expiresAt) {
        deployment = Objects.requireNonNull(deployment, "模型部署不能为空");
        var verification = ModelCapabilityVerification.capture(
                Objects.requireNonNull(verificationId, "模型能力验证标识不能为空"),
                deployment,
                Objects.requireNonNull(probe, "模型探测结果不能为空"),
                expiresAt);
        var revisedDeployment = verification.succeeded()
                ? deployment.verify(verification.id(), verification.expiresAt())
                : deployment;
        return new ModelDeploymentVerificationResult(verification, revisedDeployment);
    }
}
