package com.aiplatform.foundation.modelruntime.adapter.in.web;

/**
 * 发布模型部署的 HTTP 请求。
 */
public record ModelDeploymentPublishRequest(long expectedVersion) {
}
