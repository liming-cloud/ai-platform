package com.aiplatform.foundation.modelruntime.domain.platformsupply.service;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import java.time.Instant;
import java.util.Objects;

/** 收回平台模型能力观察、验证事实和部署状态转换一致性的无状态领域服务。 */
public final class PlatformModelDeploymentVerification {

    private PlatformModelDeploymentVerification() {
    }

    /**
     * 固化探测事实，仅当实测能力覆盖声明时验证草稿部署。
     *
     * @throws IllegalStateException 部署不是草稿时拒绝。
     */
    public static PlatformModelDeploymentVerificationResult verify(
            PlatformModelDeployment deployment,
            PlatformModelCapabilityVerificationId verificationId,
            ModelCapabilityObservation observation,
            Instant expiresAt) {
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
        var verification = PlatformModelCapabilityVerification.capture(
                Objects.requireNonNull(verificationId, "平台模型能力验证标识不能为空"),
                deployment,
                Objects.requireNonNull(observation, "模型探测结果不能为空"),
                expiresAt);
        var revised = verification.succeeded()
                ? deployment.verify(verification.id(), verification.expiresAt())
                : deployment;
        return new PlatformModelDeploymentVerificationResult(verification, revised);
    }
}
