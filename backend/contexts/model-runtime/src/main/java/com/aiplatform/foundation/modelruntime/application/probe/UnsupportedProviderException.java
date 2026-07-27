package com.aiplatform.foundation.modelruntime.application.probe;

import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import java.util.Objects;

/**
 * 请求的供应商协议未被当前运行进程装配。
 */
public final class UnsupportedProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ProviderKind providerKind;

    public UnsupportedProviderException(ProviderKind providerKind) {
        super("当前运行进程未装配供应商协议: " + Objects.requireNonNull(providerKind));
        this.providerKind = providerKind;
    }

    public ProviderKind providerKind() {
        return providerKind;
    }
}
