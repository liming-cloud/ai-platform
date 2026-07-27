package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 模型部署冻结的供应商连接不可用或修订已经漂移。
 */
public final class ModelDeploymentProviderUnavailableException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentProviderUnavailableException() {
        super(ModelRuntimeErrorCode.PROVIDER_UNAVAILABLE);
    }
}
