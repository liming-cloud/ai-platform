package com.aiplatform.foundation.assistantdelivery.adapter.in.web;

/**
 * 发布助手版本的乐观锁请求。
 */
public record AssistantVersionPublishRequest(long expectedVersion) {
}
