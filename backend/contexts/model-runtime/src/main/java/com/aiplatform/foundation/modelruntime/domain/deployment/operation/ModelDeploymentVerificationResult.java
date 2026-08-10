package com.aiplatform.foundation.modelruntime.domain.deployment.operation;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import java.util.Objects;

/**
 * 同时表达不可变验证事实与部署下一状态的领域操作结果。
 */
public record ModelDeploymentVerificationResult(
        ModelCapabilityVerification verification,
        ModelDeployment revisedDeployment) {

    public ModelDeploymentVerificationResult {
        verification = Objects.requireNonNull(verification, "模型能力验证事实不能为空");
        revisedDeployment = Objects.requireNonNull(revisedDeployment, "修订后的模型部署不能为空");
        if (!verification.tenantId().equals(revisedDeployment.tenantId())
                || !verification.deploymentId().equals(revisedDeployment.id())) {
            throw new IllegalArgumentException("模型能力验证与模型部署不一致");
        }
        if (verification.succeeded()
                && !verification.id().equals(revisedDeployment.lastVerificationId())) {
            throw new IllegalArgumentException("成功验证必须成为部署的当前验证事实");
        }
        if (!verification.succeeded()
                && revisedDeployment.lastVerificationId() != null) {
            throw new IllegalArgumentException("失败验证不得改变部署的当前验证事实");
        }
    }

    public boolean verified() {
        return verification.succeeded();
    }
}
