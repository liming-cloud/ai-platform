package com.aiplatform.foundation.assistantdelivery.api.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 助手身份的公共只读视图。
 */
public record AssistantView(
        UUID id,
        String displayName,
        int latestVersionNumber,
        long version,
        Instant createdAt) {
}
