package com.aiplatform.foundation.modelruntime.interfaces.web.provider.request;

/**
 * 轮换厂商连接 API Key 的 HTTP 写入请求。
 */
public record ProviderConnectionCredentialRotateRequest(long expectedVersion, String apiKey) {
}
