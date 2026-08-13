package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

/** 停用平台模型部署的 HTTP 请求。 */
public record PlatformModelDeploymentDisableRequest(long expectedVersion) {
}
