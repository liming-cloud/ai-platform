package com.aiplatform.foundation.modelruntime.application.catalog.provider;

/**
 * 当前租户的供应商连接数量达到演示配额。
 */
public final class ProviderConnectionQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderConnectionQuotaExceededException() {
        super("供应商连接数量已经达到租户配额");
    }
}
