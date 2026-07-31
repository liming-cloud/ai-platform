package com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request;

/**
 * 草稿助手测试请求；身份、租户和权限只能由令牌恢复。
 */
public record AssistantDraftTestRequest(String question) {
}
