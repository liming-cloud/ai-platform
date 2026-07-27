package com.aiplatform.foundation.assistantdelivery.application.catalog.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * 查询渠道可展示的最小已发布助手事实。
 */
public record QueryAssistantView(
        String assistantId,
        String displayName,
        String assistantVersionId,
        int versionNumber,
        Instant publishedAt) {

    public QueryAssistantView {
        Objects.requireNonNull(assistantId, "助手标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
        if (displayName == null || displayName.isBlank() || versionNumber < 1) {
            throw new IllegalArgumentException("查询助手展示事实不正确");
        }
    }
}
