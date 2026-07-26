package com.aiplatform.foundation.modelruntime.application.catalog;

/**
 * 当前租户已经存在同名供应商连接。
 */
public final class ProviderConnectionNameConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionNameConflictException() {
        super("当前租户已经存在同名供应商连接");
    }
}
