package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import java.util.Objects;

/**
 * 带幂等事实和乐观锁预期的助手版本发布请求。
 */
public record AssistantVersionPublication(
        AssistantVersion publishedVersion,
        long expectedVersion,
        String idempotencyKey,
        String requestFingerprint) {

    public AssistantVersionPublication {
        Objects.requireNonNull(publishedVersion, "已发布助手版本不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        if (expectedVersion < 0
                || publishedVersion.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("助手版本发布修订关系不正确");
        }
    }
}
