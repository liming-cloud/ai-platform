package com.aiplatform.foundation.modelruntime.application.provider.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 同一幂等键被用于不同供应商连接请求。
 */
public final class ProviderConnectionIdempotencyConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionIdempotencyConflictException() {
        super(ModelRuntimeErrorCode.PROVIDER_IDEMPOTENCY);
    }
}
