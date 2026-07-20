package com.aiplatform.appstudio.domain;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ChatMessageFeedback(
        TenantId tenantId,
        String feedbackId,
        String sessionId,
        String assistantMessageId,
        String userId,
        ChatMessageFeedbackRating rating,
        List<String> labels,
        String comment,
        Instant updatedAt
) implements ValueObject {
    public ChatMessageFeedback {
        tenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        feedbackId = requireText(feedbackId, "反馈 ID");
        sessionId = requireText(sessionId, "会话 ID");
        assistantMessageId = requireText(assistantMessageId, "助手消息 ID");
        userId = requireText(userId, "用户 ID");
        rating = Objects.requireNonNull(rating, "反馈评价不能为空");
        labels = labels == null ? List.of() : labels.stream()
                .map(label -> requireText(label, "反馈标签"))
                .distinct()
                .toList();
        if (labels.size() > 8) {
            throw new IllegalArgumentException("反馈标签不能超过 8 个");
        }
        comment = comment == null || comment.isBlank() ? null : comment.trim();
        updatedAt = Objects.requireNonNull(updatedAt, "反馈时间不能为空");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
