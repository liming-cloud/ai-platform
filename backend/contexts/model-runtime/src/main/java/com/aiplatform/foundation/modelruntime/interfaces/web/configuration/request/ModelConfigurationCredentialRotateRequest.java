package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request;

/**
 * 轮换模型配置 API Key 的一次性入站请求。
 */
public record ModelConfigurationCredentialRotateRequest(String apiKey) {
}
