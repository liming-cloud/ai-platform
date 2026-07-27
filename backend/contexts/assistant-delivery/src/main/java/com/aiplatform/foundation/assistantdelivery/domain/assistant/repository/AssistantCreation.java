package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import java.util.Objects;

/**
 * 带幂等事实的助手创建请求。
 */
public record AssistantCreation(
        Assistant assistant,
        String idempotencyKey,
        String requestFingerprint) {

    public AssistantCreation {
        Objects.requireNonNull(assistant, "助手不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
    }
}
