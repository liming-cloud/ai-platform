package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户的模型部署数量已经达到上限。
 */
public final class ModelDeploymentQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentQuotaExceededException() {
        super(ModelRuntimeErrorCode.DEPLOYMENT_QUOTA);
    }
}
