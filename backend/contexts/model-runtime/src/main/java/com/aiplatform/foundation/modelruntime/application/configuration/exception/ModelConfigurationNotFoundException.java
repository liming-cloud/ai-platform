package com.aiplatform.foundation.modelruntime.application.configuration.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前可信主体不可见或不存在目标模型配置，避免暴露跨范围资源信息。
 */
public final class ModelConfigurationNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelConfigurationNotFoundException() {
        super(ModelRuntimeErrorCode.MODEL_CONFIGURATION_NOT_FOUND);
    }
}
