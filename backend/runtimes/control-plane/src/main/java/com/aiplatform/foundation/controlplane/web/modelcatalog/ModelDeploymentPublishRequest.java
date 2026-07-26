package com.aiplatform.foundation.controlplane.web.modelcatalog;

/**
 * 发布模型部署的 HTTP 请求。
 */
public record ModelDeploymentPublishRequest(long expectedVersion) {
}
