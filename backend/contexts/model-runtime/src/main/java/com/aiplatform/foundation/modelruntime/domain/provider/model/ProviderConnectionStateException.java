package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.DomainException;

/**
 * 供应商连接状态不允许执行当前动作。
 */
public final class ProviderConnectionStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionStateException(String message) {
        super(ModelRuntimeErrorCode.PROVIDER_STATE);
    }
}
