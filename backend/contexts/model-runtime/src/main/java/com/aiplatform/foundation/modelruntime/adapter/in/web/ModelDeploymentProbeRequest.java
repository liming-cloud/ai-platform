package com.aiplatform.foundation.modelruntime.adapter.in.web;

/**
 * 探测模型部署的 HTTP 请求。
 */
public record ModelDeploymentProbeRequest(
        long expectedVersion,
        long timeoutMillis) {
}
