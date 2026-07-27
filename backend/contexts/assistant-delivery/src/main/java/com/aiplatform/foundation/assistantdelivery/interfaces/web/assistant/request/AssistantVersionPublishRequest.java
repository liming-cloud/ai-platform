package com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request;

/**
 * 发布助手版本的乐观锁请求。
 */
public record AssistantVersionPublishRequest(long expectedVersion) {
}
