package com.aiplatform.foundation.modelruntime.application.catalog.provider;

/**
 * 当前可信租户内不存在目标供应商连接。
 */
public final class ProviderConnectionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionNotFoundException() {
        super("供应商连接不存在");
    }
}
