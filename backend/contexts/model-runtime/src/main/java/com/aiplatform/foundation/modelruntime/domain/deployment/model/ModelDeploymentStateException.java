package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.DomainException;

/**
 * 模型部署状态不允许执行当前动作。
 */
public final class ModelDeploymentStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentStateException(String message) {
        super(ModelRuntimeErrorCode.DEPLOYMENT_STATE);
    }
}
