package com.aiplatform.foundation.modelruntime.application.provider.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 供应商连接修订已被其他请求改变。
 */
public final class ProviderConnectionConcurrentModificationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionConcurrentModificationException() {
        super(ModelRuntimeErrorCode.PROVIDER_CONCURRENT);
    }
}
