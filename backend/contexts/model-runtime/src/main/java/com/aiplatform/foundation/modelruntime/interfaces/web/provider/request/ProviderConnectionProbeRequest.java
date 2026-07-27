package com.aiplatform.foundation.modelruntime.interfaces.web.provider.request;

/**
 * 探测供应商连接的 HTTP 请求。
 */
public record ProviderConnectionProbeRequest(
        long expectedVersion,
        String modelCode,
        long timeoutMillis) {
}
