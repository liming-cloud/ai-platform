package com.aiplatform.foundation.modelruntime.application.catalog;

/**
 * 供应商连接修订已被其他请求改变。
 */
public final class ProviderConnectionConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionConcurrentModificationException() {
        super("供应商连接版本已经变化");
    }
}
