package com.aiplatform.foundation.modelruntime.application.provider.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 当前租户已经存在同名供应商连接。
 */
public final class ProviderConnectionNameConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionNameConflictException() {
        super(ModelRuntimeErrorCode.PROVIDER_NAME);
    }
}
