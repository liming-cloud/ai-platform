package com.aiplatform.foundation.modelruntime.application.catalog.provider;

/**
 * 同一幂等键被用于不同供应商连接请求。
 */
public final class ProviderConnectionIdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionIdempotencyConflictException() {
        super("幂等键已经用于不同供应商连接请求");
    }
}
