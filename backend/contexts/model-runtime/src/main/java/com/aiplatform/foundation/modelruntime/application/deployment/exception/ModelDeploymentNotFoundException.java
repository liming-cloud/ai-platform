package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户内找不到模型部署。
 */
public final class ModelDeploymentNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentNotFoundException() {
        super(ModelRuntimeErrorCode.DEPLOYMENT_NOT_FOUND);
    }
}
