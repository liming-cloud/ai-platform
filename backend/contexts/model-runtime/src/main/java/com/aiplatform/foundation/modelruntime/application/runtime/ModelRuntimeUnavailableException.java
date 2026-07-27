package com.aiplatform.foundation.modelruntime.application.runtime;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 模型部署不满足安全构造条件或缺少对应扩展时抛出。
 */
public final class ModelRuntimeUnavailableException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ModelRuntimeUnavailableException(String message) {
        super(ModelRuntimeErrorCode.RUNTIME_UNAVAILABLE);
    }
}
