package com.aiplatform.foundation.modelruntime.application.provider.exception;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 当前租户的供应商连接数量达到演示配额。
 */
public final class ProviderConnectionQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionQuotaExceededException() {
        super(ModelRuntimeErrorCode.PROVIDER_QUOTA);
    }
}
