package com.aiplatform.foundation.modelruntime.domain.platformsupply.service;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/** 平台模型能力验证与可能修订后的部署的不可变操作结果。 */
public record PlatformModelDeploymentVerificationResult(
        PlatformModelCapabilityVerification verification,
        PlatformModelDeployment revisedDeployment) implements ValueObject {
    public PlatformModelDeploymentVerificationResult {
        verification = Objects.requireNonNull(verification, "平台模型能力验证不能为空");
        revisedDeployment = Objects.requireNonNull(revisedDeployment, "修订后平台模型部署不能为空");
    }
}
