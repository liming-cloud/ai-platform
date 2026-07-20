package com.aiplatform.appstudio.domain;

public enum ChatMessageFeedbackRating {
    HELPFUL,
    UNHELPFUL;

    public static ChatMessageFeedbackRating from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("反馈评价不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("反馈评价仅支持 HELPFUL 或 UNHELPFUL", exception);
        }
    }
}
