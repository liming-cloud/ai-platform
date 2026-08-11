package com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request;

/**
 * 停用模型部署的 HTTP 请求。
 */
public record ModelDeploymentDisableRequest(long expectedVersion) {
}
