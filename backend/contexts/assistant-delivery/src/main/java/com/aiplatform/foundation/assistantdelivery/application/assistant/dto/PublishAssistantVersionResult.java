package com.aiplatform.foundation.assistantdelivery.application.assistant.dto;

import java.util.Objects;

/**
 * 助手版本发布结果及幂等重放事实。
 */
public record PublishAssistantVersionResult(
        AssistantVersionView assistantVersion,
        boolean replayed) {

    public PublishAssistantVersionResult {
        Objects.requireNonNull(assistantVersion, "助手版本视图不能为空");
    }
}
