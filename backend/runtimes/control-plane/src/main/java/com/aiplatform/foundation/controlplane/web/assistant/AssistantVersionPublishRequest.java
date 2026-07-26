package com.aiplatform.foundation.controlplane.web.assistant;

/**
 * 发布助手版本的乐观锁请求。
 */
public record AssistantVersionPublishRequest(long expectedVersion) {
}
