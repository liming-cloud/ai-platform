package com.aiplatform.foundation.modelruntime.interfaces.web.provider.request;

/** 请求受控发现某个已验证供应商连接的模型候选。 */
public record ProviderModelDiscoveryRequest(
        long expectedVersion,
        long timeoutMillis) {
}
