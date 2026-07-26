package com.aiplatform.foundation.assistantdelivery.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 仓储向应用层返回的最小已发布助手摘要。
 */
public record PublishedAssistantSummary(
        UUID assistantId,
        String displayName,
        UUID assistantVersionId,
        int versionNumber,
        Instant publishedAt) {

    public PublishedAssistantSummary {
        Objects.requireNonNull(assistantId, "助手标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
        if (displayName == null || displayName.isBlank() || versionNumber < 1) {
            throw new IllegalArgumentException("已发布助手摘要不正确");
        }
    }
}
