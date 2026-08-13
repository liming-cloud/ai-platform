package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

/** 探测平台模型部署的 HTTP 请求。 */
public record PlatformModelDeploymentProbeRequest(long expectedVersion, long timeoutMillis) {
}
