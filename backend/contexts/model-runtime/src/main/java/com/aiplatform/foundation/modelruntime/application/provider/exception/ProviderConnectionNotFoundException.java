package com.aiplatform.foundation.modelruntime.application.provider.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 当前可信租户内不存在目标供应商连接。
 */
public final class ProviderConnectionNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionNotFoundException() {
        super(ModelRuntimeErrorCode.PROVIDER_NOT_FOUND);
    }
}
