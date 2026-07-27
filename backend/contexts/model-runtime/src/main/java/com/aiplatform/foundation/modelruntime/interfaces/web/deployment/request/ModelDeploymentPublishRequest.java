package com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request;

/**
 * 发布模型部署的 HTTP 请求。
 */
public record ModelDeploymentPublishRequest(long expectedVersion) {
}
