package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import java.util.Objects;

/**
 * 在助手聚合修订边界内创建版本的事务请求。
 */
public record AssistantVersionCreation(
        Assistant expectedAssistant,
        Assistant advancedAssistant,
        AssistantVersion assistantVersion,
        String idempotencyKey,
        String requestFingerprint) {

    public AssistantVersionCreation {
        Objects.requireNonNull(expectedAssistant, "原助手不能为空");
        Objects.requireNonNull(advancedAssistant, "新助手不能为空");
        Objects.requireNonNull(assistantVersion, "助手版本不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        if (!expectedAssistant.id().equals(advancedAssistant.id())
                || !expectedAssistant.id().equals(assistantVersion.assistantId())
                || advancedAssistant.version() != expectedAssistant.version() + 1
                || advancedAssistant.latestVersionNumber()
                        != expectedAssistant.latestVersionNumber() + 1
                || assistantVersion.number() != advancedAssistant.latestVersionNumber()) {
            throw new IllegalArgumentException("助手与新版本关系不正确");
        }
    }
}
