package com.aiplatform.foundation.assistantdelivery.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import java.util.UUID;

/**
 * 发布一个冻结的助手版本。
 */
public record PublishAssistantVersionCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        UUID assistantVersionId,
        long expectedVersion) {

    public PublishAssistantVersionCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        if (idempotencyKey.isBlank()
                || !idempotencyKey.equals(idempotencyKey.trim())
                || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期修订不能为负数");
        }
    }
}
