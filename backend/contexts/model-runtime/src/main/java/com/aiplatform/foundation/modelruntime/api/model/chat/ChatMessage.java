package com.aiplatform.foundation.modelruntime.api.model.chat;

import java.util.Objects;

/**
 * 与具体模型框架无关的对话消息。
 */
public record ChatMessage(ChatRole role, String content) {

    public static final int MAX_CONTENT_CODE_POINTS = 32_768;

    public ChatMessage {
        Objects.requireNonNull(role, "对话消息角色不能为空");
        Objects.requireNonNull(content, "对话消息内容不能为空");
        if (content.isBlank()
                || content.codePointCount(0, content.length()) > MAX_CONTENT_CODE_POINTS) {
            throw new IllegalArgumentException("对话消息内容格式不正确");
        }
    }
}
