package com.aiplatform.foundation.modelruntime.application.probe.exception;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.Objects;

/**
 * 请求的供应商协议未被当前运行进程装配。
 */
public final class UnsupportedProviderException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    private final ProviderKind providerKind;

    public UnsupportedProviderException(ProviderKind providerKind) {
        super(ModelRuntimeErrorCode.UNSUPPORTED_PROVIDER);
        this.providerKind = Objects.requireNonNull(providerKind);
    }

    public ProviderKind providerKind() {
        return providerKind;
    }
}
