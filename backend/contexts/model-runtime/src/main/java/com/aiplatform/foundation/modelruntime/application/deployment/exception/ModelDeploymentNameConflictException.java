package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户已经存在同名模型部署。
 */
public final class ModelDeploymentNameConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentNameConflictException() {
        super(ModelRuntimeErrorCode.DEPLOYMENT_NAME);
    }
}
