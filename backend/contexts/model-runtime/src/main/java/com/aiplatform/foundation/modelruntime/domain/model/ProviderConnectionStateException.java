package com.aiplatform.foundation.modelruntime.domain.model;

/**
 * 供应商连接状态不允许执行当前动作。
 */
public final class ProviderConnectionStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionStateException(String message) {
        super(message);
    }
}
