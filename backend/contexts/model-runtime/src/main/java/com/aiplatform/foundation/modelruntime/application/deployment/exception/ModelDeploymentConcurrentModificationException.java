package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 模型部署已被其他请求更新。
 */
public final class ModelDeploymentConcurrentModificationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentConcurrentModificationException() {
        super(ModelRuntimeErrorCode.DEPLOYMENT_CONCURRENT);
    }
}
