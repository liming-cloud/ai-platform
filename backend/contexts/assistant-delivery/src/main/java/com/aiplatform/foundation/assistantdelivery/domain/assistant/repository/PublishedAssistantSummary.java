package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * 仓储向应用层返回的最小已发布助手摘要。
 */
public record PublishedAssistantSummary(
        long assistantId,
        String displayName,
        long assistantVersionId,
        int versionNumber,
        Instant publishedAt) {

    public PublishedAssistantSummary {
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
        if (assistantId <= 0
                || assistantVersionId <= 0
                || displayName == null
                || displayName.isBlank()
                || versionNumber < 1) {
            throw new IllegalArgumentException("已发布助手摘要不正确");
        }
    }
}
