package com.aiplatform.foundation.modelruntime.application.deployment.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/** 当前供应商连接修订没有可选择的有效模型发现候选。 */
public final class ModelDeploymentDiscoveryUnavailableException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelDeploymentDiscoveryUnavailableException() {
        super(ModelRuntimeErrorCode.DEPLOYMENT_DISCOVERY_UNAVAILABLE);
    }
}
