package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 同一模型部署幂等键承载了不同请求。
 */
public final class ModelDeploymentIdempotencyConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentIdempotencyConflictException() {
        super(ModelRuntimeErrorCode.DEPLOYMENT_IDEMPOTENCY);
    }
}
