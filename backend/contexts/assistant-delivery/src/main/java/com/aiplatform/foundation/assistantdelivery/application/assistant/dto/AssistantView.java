package com.aiplatform.foundation.assistantdelivery.application.assistant.dto;

import java.time.Instant;

/**
 * 助手身份的公共只读视图。
 */
public record AssistantView(
        String id,
        String displayName,
        int latestVersionNumber,
        long version,
        Instant createdAt) {
}
